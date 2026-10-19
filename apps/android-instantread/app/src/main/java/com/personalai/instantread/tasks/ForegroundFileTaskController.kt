package com.personalai.instantread.tasks

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancellationSource
import com.personalai.instantread.io.FileTaskCancelledException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ForegroundFileTaskController(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val callbackDispatcher: FileTaskCallbackDispatcher
) {
    private val generation = AtomicLong(0)

    @Volatile
    private var currentFuture: Future<*>? = null
    private var currentCancellationSource: FileTaskCancellationSource? = null
    private var pendingDiscard: PendingDiscard? = null
    private var closed = false

    fun <T, P> start(
        work: (FileTaskCancellationToken, (P) -> Unit) -> T,
        onProgress: (P) -> Unit,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
        onDiscardedResult: (T) -> Unit = {}
    ) {
        val scheduledTask = synchronized(this) {
            check(!closed) { "Foreground file task controller is closed" }

            val taskGeneration = generation.incrementAndGet()
            val previousFuture = currentFuture
            val previousCancellationSource = currentCancellationSource
            val previousDiscard = pendingDiscard.also { pendingDiscard = null }
            val cancellationSource = FileTaskCancellationSource {
                generation.get() != taskGeneration || Thread.currentThread().isInterrupted
            }
            val future = FutureTask<Unit> {
                runTask(
                    taskGeneration = taskGeneration,
                    cancellationToken = cancellationSource.token,
                    work = work,
                    onProgress = onProgress,
                    onSuccess = onSuccess,
                    onFailure = onFailure,
                    onDiscardedResult = onDiscardedResult
                )
            }
            currentFuture = future
            currentCancellationSource = cancellationSource
            ScheduledTask(
                taskGeneration,
                previousFuture,
                previousCancellationSource,
                previousDiscard,
                cancellationSource,
                future
            )
        }

        scheduledTask.previousCancellationSource?.cancel()
        scheduledTask.previousFuture?.cancel(true)
        scheduledTask.previousDiscard?.discard()

        try {
            executor.execute(scheduledTask.future)
        } catch (error: Throwable) {
            clearRejectedTask(scheduledTask)
            scheduledTask.cancellationSource.cancel()
            scheduledTask.future.cancel(false)
            throw error
        }
    }

    fun cancelCurrent() {
        val cancelledTask = synchronized(this) {
            generation.incrementAndGet()
            CancelledTask(
                future = currentFuture.also { currentFuture = null },
                cancellationSource = currentCancellationSource.also { currentCancellationSource = null },
                pendingDiscard = pendingDiscard.also { pendingDiscard = null }
            )
        }
        cancelledTask.cancellationSource?.cancel()
        cancelledTask.future?.cancel(true)
        cancelledTask.pendingDiscard?.discard()
    }

    fun close() {
        val cancelledTask = synchronized(this) {
            if (closed) return
            closed = true
            generation.incrementAndGet()
            CancelledTask(
                future = currentFuture.also { currentFuture = null },
                cancellationSource = currentCancellationSource.also { currentCancellationSource = null },
                pendingDiscard = pendingDiscard.also { pendingDiscard = null }
            )
        }
        cancelledTask.cancellationSource?.cancel()
        cancelledTask.future?.cancel(true)
        cancelledTask.pendingDiscard?.discard()
        executor.shutdownNow()
    }

    private fun <T, P> runTask(
        taskGeneration: Long,
        cancellationToken: FileTaskCancellationToken,
        work: (FileTaskCancellationToken, (P) -> Unit) -> T,
        onProgress: (P) -> Unit,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
        onDiscardedResult: (T) -> Unit
    ) {
        val report: (P) -> Unit = { progress ->
            callbackDispatcher.dispatch {
                deliverIfCurrent(taskGeneration) {
                    onProgress(progress)
                }
            }
        }

        val result = try {
            work(cancellationToken, report)
        } catch (_: FileTaskCancelledException) {
            clearCurrent(taskGeneration)
            return
        } catch (_: InterruptedException) {
            clearCurrent(taskGeneration)
            return
        } catch (_: CancellationException) {
            clearCurrent(taskGeneration)
            return
        } catch (error: Throwable) {
            dispatchTerminal(taskGeneration) {
                onFailure(error)
            }
            return
        }

        val ownedResult = OwnedResult(result, onDiscardedResult)
        try {
            cancellationToken.throwIfCancelled()
        } catch (_: FileTaskCancelledException) {
            clearCurrent(taskGeneration)
            ownedResult.discard()
            return
        }
        if (!registerPendingDiscard(taskGeneration, ownedResult::discard)) {
            ownedResult.discard()
            return
        }
        dispatchSuccess(taskGeneration, ownedResult, onSuccess)
    }

    private fun deliverIfCurrent(taskGeneration: Long, callback: () -> Unit) {
        val shouldDeliver = synchronized(this) { isCurrent(taskGeneration) }
        if (shouldDeliver) callback()
    }

    private fun deliverTerminalIfCurrent(taskGeneration: Long, callback: () -> Unit) {
        val shouldDeliver = synchronized(this) {
            if (!isCurrent(taskGeneration)) {
                false
            } else {
                currentFuture = null
                currentCancellationSource = null
                pendingDiscard = null
                true
            }
        }
        if (shouldDeliver) callback()
    }

    private fun dispatchTerminal(taskGeneration: Long, callback: () -> Unit) {
        try {
            callbackDispatcher.dispatch {
                deliverTerminalIfCurrent(taskGeneration, callback)
            }
        } catch (error: Throwable) {
            clearCurrent(taskGeneration)
            throw error
        }
    }

    private fun <T> dispatchSuccess(
        taskGeneration: Long,
        ownedResult: OwnedResult<T>,
        onSuccess: (T) -> Unit
    ) {
        try {
            callbackDispatcher.dispatch {
                val shouldPublish = synchronized(this) {
                    if (!isCurrent(taskGeneration)) {
                        false
                    } else {
                        currentFuture = null
                        currentCancellationSource = null
                        pendingDiscard = null
                        true
                    }
                }
                if (shouldPublish) ownedResult.publish(onSuccess) else ownedResult.discard()
            }
        } catch (_: Throwable) {
            takePendingDiscard(taskGeneration)?.discard()
            ownedResult.discard()
        }
    }

    private fun registerPendingDiscard(
        taskGeneration: Long,
        discard: () -> Unit
    ): Boolean = synchronized(this) {
        if (!isCurrent(taskGeneration)) {
            false
        } else {
            pendingDiscard = PendingDiscard(taskGeneration, discard)
            true
        }
    }

    private fun takePendingDiscard(taskGeneration: Long): PendingDiscard? = synchronized(this) {
        if (generation.get() != taskGeneration) {
            null
        } else {
            currentFuture = null
            currentCancellationSource = null
            pendingDiscard.also { pendingDiscard = null }
        }
    }

    private fun clearCurrent(taskGeneration: Long) {
        synchronized(this) {
            if (generation.get() == taskGeneration) {
                currentFuture = null
                currentCancellationSource = null
            }
        }
    }

    private fun isCurrent(taskGeneration: Long): Boolean =
        generation.get() == taskGeneration && currentFuture != null

    private fun clearRejectedTask(task: ScheduledTask) {
        synchronized(this) {
            if (generation.get() == task.generation && currentFuture === task.future) {
                currentFuture = null
                currentCancellationSource = null
            }
        }
    }

    private data class ScheduledTask(
        val generation: Long,
        val previousFuture: Future<*>?,
        val previousCancellationSource: FileTaskCancellationSource?,
        val previousDiscard: PendingDiscard?,
        val cancellationSource: FileTaskCancellationSource,
        val future: FutureTask<Unit>
    )

    private data class CancelledTask(
        val future: Future<*>?,
        val cancellationSource: FileTaskCancellationSource?,
        val pendingDiscard: PendingDiscard?
    )

    private data class PendingDiscard(
        val generation: Long,
        val action: () -> Unit
    ) {
        fun discard() = action()
    }

    private class OwnedResult<T>(
        private val value: T,
        private val onDiscardedResult: (T) -> Unit
    ) {
        private val consumed = AtomicBoolean(false)

        fun publish(onSuccess: (T) -> Unit) {
            if (consumed.compareAndSet(false, true)) onSuccess(value)
        }

        fun discard() {
            if (!consumed.compareAndSet(false, true)) return
            try {
                onDiscardedResult(value)
            } catch (_: Exception) {
                // A failed cleanup must not publish a stale result.
            }
        }
    }
}
