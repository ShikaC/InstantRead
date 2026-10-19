package com.personalai.instantread.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException

class ForegroundFileTaskControllerTest {
    @Test
    fun stagesExposeConfirmedChineseLabels() {
        assertEquals("正在读取文件信息", ForegroundTaskStage.READING_METADATA.label)
        assertEquals("正在导入文件", ForegroundTaskStage.IMPORTING_FILE.label)
        assertEquals("正在检查文本编码", ForegroundTaskStage.CHECKING_TEXT_ENCODING.label)
        assertEquals("正在生成阅读页", ForegroundTaskStage.GENERATING_READING_PAGE.label)
        assertEquals("正在安全解压 ZIP", ForegroundTaskStage.EXTRACTING_ZIP.label)
    }

    @Test
    fun percentIsUnknownWithoutAPositiveTotal() {
        assertNull(state(completed = 10, total = null).percent)
        assertNull(state(completed = 10, total = 0).percent)
        assertNull(state(completed = 10, total = -1).percent)
    }

    @Test
    fun percentTreatsMissingOrNegativeCompletedBytesAsZero() {
        assertEquals(0, state(completed = null, total = 100).percent)
        assertEquals(0, state(completed = -1, total = 100).percent)
    }

    @Test
    fun percentClampsCompletedBytesAtOneHundred() {
        assertEquals(100, state(completed = 100, total = 100).percent)
        assertEquals(100, state(completed = 101, total = 100).percent)
        assertEquals(100, state(completed = Long.MAX_VALUE, total = Long.MAX_VALUE).percent)
    }

    @Test
    fun percentCalculatesLargeIntermediateValuesWithoutOverflow() {
        assertEquals(49, state(completed = Long.MAX_VALUE / 2, total = Long.MAX_VALUE).percent)
        assertEquals(99, state(completed = Long.MAX_VALUE - 1, total = Long.MAX_VALUE).percent)
    }

    @Test
    fun startingNewTaskSuppressesQueuedSuccessFromOldTask() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "first" },
            onProgress = {},
            onSuccess = { events += it },
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        assertEquals(1, callbacks.pending)

        controller.start<String, String>(
            work = { _, _ -> "second" },
            onProgress = {},
            onSuccess = { events += it },
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf("second"), events)
        controller.close()
    }

    @Test
    fun currentSuccessIsPublishedWithoutDiscardingResult() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()
        val discarded = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "current" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = discarded::add
        )
        worker.runNext()
        callbacks.runAll()
        controller.cancelCurrent()
        controller.close()

        assertEquals(listOf("current"), successes)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun cancellingQueuedSuccessDiscardsResultExactlyOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()
        val discarded = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "cancelled" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = discarded::add
        )
        worker.runNext()
        controller.cancelCurrent()
        assertEquals(listOf("cancelled"), discarded)
        callbacks.runAll()
        controller.close()

        assertTrue(successes.isEmpty())
        assertEquals(listOf("cancelled"), discarded)
    }

    @Test
    fun startingNewTaskDiscardsOldQueuedResultExactlyOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()
        val discarded = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "old" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = discarded::add
        )
        worker.runNext()
        controller.start<String, String>(
            work = { _, _ -> "new" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = discarded::add
        )
        assertEquals(listOf("old"), discarded)
        worker.runNext()
        callbacks.runAll()
        controller.close()

        assertEquals(listOf("new"), successes)
        assertEquals(listOf("old"), discarded)
    }

    @Test
    fun closingWithQueuedSuccessDiscardsResultExactlyOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()
        val discarded = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "closed" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = discarded::add
        )
        worker.runNext()
        controller.close()
        assertEquals(listOf("closed"), discarded)
        callbacks.runAll()
        controller.close()

        assertTrue(successes.isEmpty())
        assertEquals(listOf("closed"), discarded)
    }

    @Test
    fun successDispatchFailureDiscardsResultExactlyOnce() {
        val worker = ManualExecutorService()
        val callbacks = ScriptedCallbackDispatcher(failingDispatch("success"))
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()
        val discarded = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "undeliverable" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = discarded::add
        )
        worker.runNext()
        controller.cancelCurrent()
        controller.close()

        assertTrue(successes.isEmpty())
        assertEquals(listOf("undeliverable"), discarded)
    }

    @Test
    fun discardedResultCleanupRunsOutsideControllerMonitor() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        lateinit var controller: ForegroundFileTaskController
        controller = ForegroundFileTaskController(worker, callbacks)

        controller.start<String, String>(
            work = { _, _ -> "discard" },
            onProgress = {},
            onSuccess = { throw AssertionError(it) },
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = { assertFalse(Thread.holdsLock(controller)) }
        )
        worker.runNext()
        controller.cancelCurrent()
        callbacks.runAll()
        controller.close()
    }

    @Test
    fun discardedResultCleanupFailureNeverPublishesStaleResult() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()
        var discardAttempts = 0

        controller.start<String, String>(
            work = { _, _ -> "stale" },
            onProgress = {},
            onSuccess = successes::add,
            onFailure = { throw AssertionError(it) },
            onDiscardedResult = {
                discardAttempts += 1
                throw IllegalStateException("cleanup failed")
            }
        )
        worker.runNext()
        controller.cancelCurrent()
        callbacks.runAll()
        controller.close()

        assertTrue(successes.isEmpty())
        assertEquals(1, discardAttempts)
    }

    @Test
    fun cancelCurrentNotifiesCancellationListenerOnceOutsideControllerMonitor() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        lateinit var controller: ForegroundFileTaskController
        controller = ForegroundFileTaskController(worker, callbacks)
        var listenerCalls = 0

        controller.start<String, String>(
            work = { token, _ ->
                token.invokeOnCancellation {
                    assertFalse(Thread.holdsLock(controller))
                    listenerCalls += 1
                }
                "done"
            },
            onProgress = {},
            onSuccess = {},
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        controller.cancelCurrent()
        callbacks.runAll()
        controller.close()

        assertEquals(1, listenerCalls)
    }

    @Test
    fun startingNewTaskNotifiesOldCancellationListenerOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        var listenerCalls = 0

        controller.start<String, String>(
            work = { token, _ ->
                token.invokeOnCancellation { listenerCalls += 1 }
                "old"
            },
            onProgress = {},
            onSuccess = {},
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        controller.start<String, String>(
            work = { _, _ -> "new" },
            onProgress = {},
            onSuccess = {},
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        callbacks.runAll()
        controller.close()

        assertEquals(1, listenerCalls)
    }

    @Test
    fun closeNotifiesCurrentCancellationListenerOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        var listenerCalls = 0

        controller.start<String, String>(
            work = { token, _ ->
                token.invokeOnCancellation { listenerCalls += 1 }
                "done"
            },
            onProgress = {},
            onSuccess = {},
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        controller.close()
        callbacks.runAll()
        controller.close()

        assertEquals(1, listenerCalls)
    }

    @Test
    fun startingNewTaskSuppressesQueuedProgressAndFailureFromOldTask() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, report ->
                report("old-progress")
                throw IllegalStateException("old-failure")
            },
            onProgress = { events += "progress:$it" },
            onSuccess = { events += "success:$it" },
            onFailure = { events += "failure:${it.message}" }
        )
        worker.runNext()
        assertEquals(2, callbacks.pending)

        controller.start<String, String>(
            work = { _, report ->
                report("new-progress")
                "new-success"
            },
            onProgress = { events += "progress:$it" },
            onSuccess = { events += "success:$it" },
            onFailure = { events += "failure:${it.message}" }
        )
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf("progress:new-progress", "success:new-success"), events)
        controller.close()
    }

    @Test
    fun cancellingBeforeExecutionPreventsWorkAndCallbacks() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        var workRan = false
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> workRan = true; "done" },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        controller.cancelCurrent()
        worker.runAll()
        callbacks.runAll()

        assertFalse(workRan)
        assertTrue(events.isEmpty())
        controller.close()
    }

    @Test
    fun cancellingDuringExecutionSuppressesQueuedProgressAndSuccess() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        lateinit var controller: ForegroundFileTaskController
        controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, report ->
                report("progress")
                controller.cancelCurrent()
                "done"
            },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    @Test
    fun cancellingAfterWorkerCompletesSuppressesQueuedCallbacks() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, report -> report("progress"); "done" },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        assertEquals(2, callbacks.pending)
        controller.cancelCurrent()
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    @Test
    fun ordinaryFailureIsDeliveredExactlyOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val failures = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> throw IllegalStateException("boom") },
            onProgress = {},
            onSuccess = { throw AssertionError(it) },
            onFailure = { failures += it.message.orEmpty() }
        )
        worker.runNext()
        callbacks.runAll()
        callbacks.runAll()
        controller.cancelCurrent()

        assertEquals(listOf("boom"), failures)
        controller.close()
    }

    @Test
    fun successIsDeliveredExactlyOnceAndLaterCancellationDoesNotChangeIt() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val successes = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "done" },
            onProgress = {},
            onSuccess = { successes += it },
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        callbacks.runAll()
        callbacks.runAll()
        controller.cancelCurrent()

        assertEquals(listOf("done"), successes)
        controller.close()
    }

    @Test
    fun interruptedWorkerThreadIsNormalCancellation() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { token, _ ->
                Thread.currentThread().interrupt()
                token.throwIfCancelled()
                "unexpected"
            },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    @Test
    fun interruptedExceptionIsNormalCancellation() {
        assertThrownCancellationHasNoCallbacks { throw InterruptedException("cancel") }
    }

    @Test
    fun cancellationExceptionIsNormalCancellation() {
        assertThrownCancellationHasNoCallbacks { throw CancellationException("cancel") }
    }

    @Test
    fun progressCallbacksKeepTheirReportedOrder() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, report ->
                report("one")
                report("two")
                report("three")
                "done"
            },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf("one", "two", "three", "done"), events)
        controller.close()
    }

    @Test
    fun closeCancelsPendingWorkerTaskAndShutsExecutorDownOnce() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        var workRan = false

        controller.start<String, String>(
            work = { _, _ -> workRan = true; "done" },
            onProgress = {},
            onSuccess = {},
            onFailure = {}
        )
        assertEquals(1, worker.pending)
        controller.close()
        controller.close()

        assertFalse(workRan)
        assertEquals(0, worker.pending)
        assertTrue(worker.isShutdown)
        assertEquals(1, worker.shutdownNowCallCount)
        assertEquals(0, callbacks.pending)
    }

    @Test
    fun closeSuppressesCallbacksAlreadyQueuedByCompletedWorker() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, report -> report("progress"); "done" },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        assertEquals(2, callbacks.pending)
        controller.close()
        callbacks.runAll()

        assertTrue(events.isEmpty())
    }

    @Test
    fun inlineExecutorDoesNotRunInsideControllerMonitor() {
        val callbacks = QueuedCallbackDispatcher()
        lateinit var controller: ForegroundFileTaskController
        val executor = InlineExecutorService(
            beforeExecute = { assertFalse(Thread.holdsLock(controller)) }
        )
        controller = ForegroundFileTaskController(executor, callbacks)
        val successes = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> "done" },
            onProgress = {},
            onSuccess = { successes += it },
            onFailure = { throw AssertionError(it) }
        )
        callbacks.runAll()

        assertEquals(listOf("done"), successes)
        controller.close()
    }

    @Test
    fun rejectedExecutionClearsCurrentAndAllowsNextStart() {
        val callbacks = QueuedCallbackDispatcher()
        val executor = InlineExecutorService(rejectionsBeforeExecution = 1)
        val controller = ForegroundFileTaskController(executor, callbacks)
        val successes = mutableListOf<String>()

        assertThrows(RejectedExecutionException::class.java) {
            controller.start<String, String>(
                work = { _, _ -> "rejected" },
                onProgress = {},
                onSuccess = { successes += it },
                onFailure = { throw AssertionError(it) }
            )
        }
        controller.start<String, String>(
            work = { _, _ -> "accepted" },
            onProgress = {},
            onSuccess = { successes += it },
            onFailure = { throw AssertionError(it) }
        )
        callbacks.runAll()

        assertEquals(listOf("accepted"), successes)
        controller.close()
    }

    @Test
    fun progressCallbackRunsOutsideControllerMonitor() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)

        controller.start<String, String>(
            work = { _, report -> report("progress"); "done" },
            onProgress = { assertFalse(Thread.holdsLock(controller)) },
            onSuccess = {},
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        callbacks.runNext()

        controller.close()
    }

    @Test
    fun successCallbackRunsOutsideControllerMonitor() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)

        controller.start<String, String>(
            work = { _, _ -> "done" },
            onProgress = {},
            onSuccess = { assertFalse(Thread.holdsLock(controller)) },
            onFailure = { throw AssertionError(it) }
        )
        worker.runNext()
        callbacks.runNext()

        controller.close()
    }

    @Test
    fun failureCallbackRunsOutsideControllerMonitor() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)

        controller.start<String, String>(
            work = { _, _ -> throw IllegalStateException("boom") },
            onProgress = {},
            onSuccess = { throw AssertionError(it) },
            onFailure = { assertFalse(Thread.holdsLock(controller)) }
        )
        worker.runNext()
        callbacks.runNext()

        controller.close()
    }

    @Test
    fun successDispatchFailureClearsCurrentAndSuppressesLateProgress() {
        val worker = ManualExecutorService()
        val callbacks = ScriptedCallbackDispatcher(failingDispatch("success"))
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()
        lateinit var lateReport: (String) -> Unit

        controller.start<String, String>(
            work = { _, report -> lateReport = report; "done" },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        lateReport("late-progress")
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    @Test
    fun failureDispatchFailureClearsCurrentAndSuppressesLateProgress() {
        val worker = ManualExecutorService()
        val callbacks = ScriptedCallbackDispatcher(failingDispatch("failure"))
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()
        lateinit var lateReport: (String) -> Unit

        controller.start<String, String>(
            work = { _, report ->
                lateReport = report
                throw IllegalStateException("boom")
            },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        lateReport("late-progress")
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    @Test
    fun progressAndFailureDispatchFailuresClearCurrentAndSuppressLateProgress() {
        val worker = ManualExecutorService()
        val callbacks = ScriptedCallbackDispatcher(
            failingDispatch("progress"),
            failingDispatch("failure")
        )
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()
        lateinit var lateReport: (String) -> Unit

        controller.start<String, String>(
            work = { _, report ->
                lateReport = report
                report("initial-progress")
                "unexpected"
            },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        lateReport("late-progress")
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    private fun state(completed: Long?, total: Long?): ForegroundTaskState =
        ForegroundTaskState(
            displayName = "file.txt",
            stage = ForegroundTaskStage.IMPORTING_FILE,
            completedBytes = completed,
            totalBytes = total
        )

    private fun assertThrownCancellationHasNoCallbacks(
        throwCancellation: () -> Nothing
    ) {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val controller = ForegroundFileTaskController(worker, callbacks)
        val events = mutableListOf<String>()

        controller.start<String, String>(
            work = { _, _ -> throwCancellation() },
            onProgress = { events += it },
            onSuccess = { events += it },
            onFailure = { events += "failure" }
        )
        worker.runNext()
        callbacks.runAll()

        assertTrue(events.isEmpty())
        controller.close()
    }

    private fun failingDispatch(name: String): CallbackDispatchAction =
        CallbackDispatchAction.Fail(IllegalStateException("$name dispatch failed"))
}
