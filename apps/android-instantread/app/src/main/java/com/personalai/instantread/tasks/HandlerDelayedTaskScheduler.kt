package com.personalai.instantread.tasks

import android.os.Handler

class HandlerDelayedTaskScheduler(
    private val handler: Handler
) : DelayedTaskScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): DelayedTaskHandle {
        require(delayMillis >= 0)
        val runnable = Runnable(action)
        check(handler.postDelayed(runnable, delayMillis)) { "Handler rejected delayed task" }
        return DelayedTaskHandle { handler.removeCallbacks(runnable) }
    }
}
