package com.personalai.instantread.tasks

import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class ManualExecutorService : AbstractExecutorService() {
    private val tasks = ArrayDeque<Runnable>()
    private var stopped = false

    var shutdownNowCallCount: Int = 0
        private set

    val pending: Int
        get() = tasks.size

    override fun execute(command: Runnable) {
        if (stopped) throw RejectedExecutionException("Executor is shut down")
        tasks.addLast(command)
    }

    fun runNext() {
        try {
            tasks.removeFirst().run()
        } finally {
            Thread.interrupted()
        }
    }

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }

    override fun shutdown() {
        stopped = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        shutdownNowCallCount += 1
        return tasks.toMutableList().also {
            tasks.clear()
            stopped = true
        }
    }

    override fun isShutdown(): Boolean = stopped

    override fun isTerminated(): Boolean = stopped && tasks.isEmpty()

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
}

class InlineExecutorService(
    private val beforeExecute: () -> Unit = {},
    rejectionsBeforeExecution: Int = 0
) : AbstractExecutorService() {
    private var stopped = false
    private var remainingRejections = rejectionsBeforeExecution

    init {
        require(rejectionsBeforeExecution >= 0)
    }

    override fun execute(command: Runnable) {
        if (stopped) throw RejectedExecutionException("Executor is shut down")
        beforeExecute()
        if (remainingRejections > 0) {
            remainingRejections -= 1
            throw RejectedExecutionException("Scripted rejection")
        }
        command.run()
    }

    override fun shutdown() {
        stopped = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        stopped = true
        return mutableListOf()
    }

    override fun isShutdown(): Boolean = stopped

    override fun isTerminated(): Boolean = stopped

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
}

class QueuedCallbackDispatcher : FileTaskCallbackDispatcher {
    private val callbacks = ArrayDeque<() -> Unit>()

    val pending: Int
        get() = callbacks.size

    override fun dispatch(block: () -> Unit) {
        callbacks.addLast(block)
    }

    fun runNext() {
        callbacks.removeFirst().invoke()
    }

    fun runAll() {
        while (callbacks.isNotEmpty()) runNext()
    }
}

sealed class CallbackDispatchAction {
    data object Enqueue : CallbackDispatchAction()
    data class Fail(val error: Throwable) : CallbackDispatchAction()
}

class ScriptedCallbackDispatcher(
    vararg actions: CallbackDispatchAction
) : FileTaskCallbackDispatcher {
    private val actions = ArrayDeque(actions.toList())
    private val callbacks = ArrayDeque<() -> Unit>()

    var dispatchAttempts: Int = 0
        private set

    val pending: Int
        get() = callbacks.size

    override fun dispatch(block: () -> Unit) {
        dispatchAttempts += 1
        when (val action = actions.pollFirst() ?: CallbackDispatchAction.Enqueue) {
            CallbackDispatchAction.Enqueue -> callbacks.addLast(block)
            is CallbackDispatchAction.Fail -> throw action.error
        }
    }

    fun runAll() {
        while (callbacks.isNotEmpty()) callbacks.removeFirst().invoke()
    }
}

class ManualDelayedTaskScheduler : DelayedTaskScheduler {
    private data class Scheduled(
        val dueAtMillis: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false
    )

    private val scheduled = mutableListOf<Scheduled>()
    private var nowMillis = 0L

    override fun schedule(delayMillis: Long, action: () -> Unit): DelayedTaskHandle {
        require(delayMillis >= 0)
        val task = Scheduled(nowMillis + delayMillis, action)
        scheduled += task
        return DelayedTaskHandle { task.cancelled = true }
    }

    fun advanceBy(milliseconds: Long) {
        require(milliseconds >= 0)
        val targetMillis = nowMillis + milliseconds
        while (true) {
            scheduled.removeAll { it.cancelled }
            val next = scheduled
                .filter { it.dueAtMillis <= targetMillis }
                .minByOrNull { it.dueAtMillis }
                ?: run {
                    nowMillis = targetMillis
                    return
                }
            scheduled.remove(next)
            nowMillis = next.dueAtMillis
            next.action()
        }
    }
}
