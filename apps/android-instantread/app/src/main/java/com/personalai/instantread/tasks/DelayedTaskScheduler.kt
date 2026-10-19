package com.personalai.instantread.tasks

fun interface DelayedTaskHandle {
    fun cancel()
}

fun interface DelayedTaskScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): DelayedTaskHandle
}
