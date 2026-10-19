package com.personalai.instantread.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class PreviewTaskPresentationControllerTest {
    private val checkingState = ForegroundTaskState(
        displayName = "sample.md",
        stage = ForegroundTaskStage.CHECKING_TEXT_ENCODING
    )
    private val generatingState = ForegroundTaskState(
        displayName = "sample.md",
        stage = ForegroundTaskStage.GENERATING_READING_PAGE
    )

    @Test
    fun fastCompletionDoesNotRenderTaskPage() {
        val scheduler = ManualDelayedTaskScheduler()
        val rendered = mutableListOf<ForegroundTaskState>()
        val controller = PreviewTaskPresentationController(scheduler)

        controller.begin(checkingState, rendered::add)
        controller.update(generatingState)
        scheduler.advanceBy(299)
        controller.complete()
        scheduler.advanceBy(1)

        assertTrue(rendered.isEmpty())
        assertFalse(controller.isPending)
    }

    @Test
    fun thresholdRendersOnlyLatestBufferedStage() {
        val scheduler = ManualDelayedTaskScheduler()
        val rendered = mutableListOf<ForegroundTaskState>()
        val controller = PreviewTaskPresentationController(scheduler)

        controller.begin(checkingState, rendered::add)
        controller.update(generatingState)
        scheduler.advanceBy(299)

        assertTrue(rendered.isEmpty())

        scheduler.advanceBy(1)

        assertEquals(listOf(generatingState), rendered)
    }

    @Test
    fun visibleTaskPageReceivesUpdatesImmediately() {
        val scheduler = ManualDelayedTaskScheduler()
        val rendered = mutableListOf<ForegroundTaskState>()
        val controller = PreviewTaskPresentationController(scheduler)

        controller.begin(checkingState, rendered::add)
        scheduler.advanceBy(300)
        controller.update(generatingState)

        assertEquals(listOf(checkingState, generatingState), rendered)
    }

    @Test
    fun cancellationSuppressesDelayedReveal() {
        val scheduler = ManualDelayedTaskScheduler()
        val rendered = mutableListOf<ForegroundTaskState>()
        val controller = PreviewTaskPresentationController(scheduler)

        controller.begin(checkingState, rendered::add)
        controller.cancel()
        scheduler.advanceBy(300)

        assertTrue(rendered.isEmpty())
        assertFalse(controller.isPending)
    }

    @Test
    fun replacementGenerationSuppressesLateOlderCallback() {
        val scheduler = LateCallbackScheduler()
        val rendered = mutableListOf<ForegroundTaskState>()
        val controller = PreviewTaskPresentationController(scheduler)
        val replacement = checkingState.copy(displayName = "replacement.md")

        controller.begin(checkingState, rendered::add)
        controller.begin(replacement, rendered::add)

        scheduler.runScheduled(index = 0)
        assertTrue(rendered.isEmpty())

        scheduler.runScheduled(index = 1)
        assertEquals(listOf(replacement), rendered)
    }

    @Test
    fun isPendingTracksActivePresentationUntilCompleteOrCancel() {
        val scheduler = ManualDelayedTaskScheduler()
        val controller = PreviewTaskPresentationController(scheduler)

        assertFalse(controller.isPending)

        controller.begin(checkingState) {}
        assertTrue(controller.isPending)
        scheduler.advanceBy(300)
        assertTrue(controller.isPending)

        controller.complete()
        assertFalse(controller.isPending)

        controller.begin(checkingState) {}
        assertTrue(controller.isPending)
        controller.cancel()
        assertFalse(controller.isPending)
    }

    @Test
    fun synchronousRevealReentrancyPreservesReplacementHandleForCancellation() {
        val scheduler = FirstScheduleRunsSynchronously()
        val controller = PreviewTaskPresentationController(scheduler)
        val replacement = checkingState.copy(displayName = "replacement.md")

        controller.begin(checkingState) {
            controller.begin(replacement) {}
        }

        assertEquals(2, scheduler.handles.size)
        assertTrue(scheduler.handles[0].cancelled)
        assertFalse(scheduler.handles[1].cancelled)

        controller.cancel()

        assertTrue(scheduler.handles[1].cancelled)
    }

    @Test
    fun manualSchedulerRunsNestedTasksAtTheirActualDueTimes() {
        val scheduler = ManualDelayedTaskScheduler()
        val events = mutableListOf<String>()

        scheduler.schedule(100) {
            events += "t=100"
            scheduler.schedule(50) { events += "t=150" }
        }

        scheduler.advanceBy(200)

        assertEquals(listOf("t=100", "t=150"), events)
    }

    @Test
    fun manualSchedulerRejectsNegativeTimeValues() {
        val scheduler = ManualDelayedTaskScheduler()

        assertThrows(IllegalArgumentException::class.java) {
            scheduler.schedule(-1) {}
        }
        assertThrows(IllegalArgumentException::class.java) {
            scheduler.advanceBy(-1)
        }
    }

    @Test
    fun stateAccessRejectsCallsFromOtherThreads() {
        val operations: List<(PreviewTaskPresentationController) -> Unit> = listOf(
            { controller -> controller.isPending.let {} },
            { controller -> controller.begin(checkingState) {} },
            { controller -> controller.update(generatingState) },
            { controller -> controller.complete() },
            { controller -> controller.cancel() }
        )

        val failures = operations.map { operation ->
            val controller = PreviewTaskPresentationController(ManualDelayedTaskScheduler())
            runOnOtherThread { operation(controller) }
        }

        assertTrue(failures.all { it is IllegalStateException })
    }

    @Test
    fun delayedRevealRejectsExecutionFromAnotherThread() {
        val scheduler = ManualDelayedTaskScheduler()
        val controller = PreviewTaskPresentationController(scheduler)
        controller.begin(checkingState) {}

        val failure = runOnOtherThread { scheduler.advanceBy(300) }

        assertTrue(failure is IllegalStateException)
        controller.cancel()
    }

    private fun runOnOtherThread(action: () -> Unit): Throwable? {
        val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            failure.set(runCatching(action).exceptionOrNull())
        }
        thread.start()
        thread.join()
        return failure.get()
    }

    private class LateCallbackScheduler : DelayedTaskScheduler {
        private val actions = mutableListOf<() -> Unit>()

        override fun schedule(delayMillis: Long, action: () -> Unit): DelayedTaskHandle {
            actions += action
            return DelayedTaskHandle {}
        }

        fun runScheduled(index: Int) {
            actions[index].invoke()
        }
    }

    private class FirstScheduleRunsSynchronously : DelayedTaskScheduler {
        val handles = mutableListOf<RecordingDelayedTaskHandle>()

        override fun schedule(delayMillis: Long, action: () -> Unit): DelayedTaskHandle {
            val handle = RecordingDelayedTaskHandle()
            handles += handle
            if (handles.size == 1) action()
            return handle
        }
    }

    private class RecordingDelayedTaskHandle : DelayedTaskHandle {
        var cancelled = false
            private set

        override fun cancel() {
            cancelled = true
        }
    }
}
