package com.personalai.instantread.tasks

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class HandlerDelayedTaskSchedulerTest {
    @Test
    fun scheduledActionRunsOnlyAfterRequestedDelay() {
        val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
        val scheduler = HandlerDelayedTaskScheduler(Handler(Looper.getMainLooper()))
        var calls = 0

        scheduler.schedule(300) { calls += 1 }
        shadowLooper.idleFor(299, TimeUnit.MILLISECONDS)
        assertEquals(0, calls)

        shadowLooper.idleFor(1, TimeUnit.MILLISECONDS)
        assertEquals(1, calls)
    }

    @Test
    fun cancellingTwiceDoesNotThrowOrRunAction() {
        val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
        val scheduler = HandlerDelayedTaskScheduler(Handler(Looper.getMainLooper()))
        var calls = 0

        val handle = scheduler.schedule(300) { calls += 1 }
        handle.cancel()
        handle.cancel()
        shadowLooper.idleFor(300, TimeUnit.MILLISECONDS)

        assertEquals(0, calls)
    }

    @Test
    fun cancellingOneScheduleDoesNotCancelAnotherForSameAction() {
        val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
        val scheduler = HandlerDelayedTaskScheduler(Handler(Looper.getMainLooper()))
        var calls = 0
        val action = { calls += 1 }

        val first = scheduler.schedule(300, action)
        scheduler.schedule(300, action)
        first.cancel()
        shadowLooper.idleFor(300, TimeUnit.MILLISECONDS)

        assertEquals(1, calls)
    }

    @Test
    fun negativeDelayIsRejected() {
        val scheduler = HandlerDelayedTaskScheduler(Handler(Looper.getMainLooper()))

        assertThrows(IllegalArgumentException::class.java) {
            scheduler.schedule(-1) {}
        }
    }

    @Test
    fun rejectedTaskThrowsInsteadOfBeingSilentlyDropped() {
        val handlerThread = HandlerThread("rejected-delayed-task").apply { start() }
        val handler = Handler(handlerThread.looper)
        handlerThread.quit()
        assertFalse(handler.postDelayed({}, 300))
        val scheduler = HandlerDelayedTaskScheduler(handler)

        val error = assertThrows(IllegalStateException::class.java) {
            scheduler.schedule(300) {}
        }

        assertEquals("Handler rejected delayed task", error.message)
    }
}
