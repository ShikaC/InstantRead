package com.personalai.instantread.tasks

/**
 * Buffers foreground task presentation behind a reveal delay.
 *
 * All state access and delayed scheduler actions must run on the thread that creates this
 * controller.
 */
class PreviewTaskPresentationController(
    private val scheduler: DelayedTaskScheduler,
    private val revealDelayMillis: Long = DEFAULT_REVEAL_DELAY_MILLIS
) {
    private val ownerThread = Thread.currentThread()
    private var generation = 0L
    private var pendingReveal: DelayedTaskHandle? = null
    private var latestState: ForegroundTaskState? = null
    private var render: ((ForegroundTaskState) -> Unit)? = null
    private var visible = false

    val isPending: Boolean
        get() {
            checkOwnerThread()
            return render != null
        }

    init {
        require(revealDelayMillis >= 0)
    }

    fun begin(initialState: ForegroundTaskState, onRender: (ForegroundTaskState) -> Unit) {
        checkOwnerThread()
        generation += 1
        val previousReveal = pendingReveal
        pendingReveal = null
        previousReveal?.cancel()
        latestState = initialState
        render = onRender
        visible = false
        val scheduledGeneration = generation
        val scheduledReveal = scheduler.schedule(revealDelayMillis) {
            checkOwnerThread()
            if (scheduledGeneration == generation) {
                pendingReveal = null
                visible = true
                latestState?.let(onRender)
            }
        }
        if (scheduledGeneration == generation && !visible) {
            pendingReveal = scheduledReveal
        } else {
            scheduledReveal.cancel()
        }
    }

    fun update(state: ForegroundTaskState) {
        checkOwnerThread()
        if (render == null) return
        latestState = state
        if (visible) render?.invoke(state)
    }

    fun complete() = cancel()

    fun cancel() {
        checkOwnerThread()
        generation += 1
        pendingReveal?.cancel()
        pendingReveal = null
        latestState = null
        render = null
        visible = false
    }

    private fun checkOwnerThread() {
        check(Thread.currentThread() === ownerThread) {
            "PreviewTaskPresentationController must be accessed from its creating thread."
        }
    }

    private companion object {
        const val DEFAULT_REVEAL_DELAY_MILLIS = 300L
    }
}
