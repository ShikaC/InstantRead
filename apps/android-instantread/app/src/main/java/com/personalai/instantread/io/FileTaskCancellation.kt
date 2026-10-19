package com.personalai.instantread.io

import java.io.InterruptedIOException

class FileTaskCancelledException : InterruptedIOException("File task cancelled")

fun interface FileTaskCancellationToken {
    fun throwIfCancelled()

    fun invokeOnCancellation(listener: () -> Unit): FileTaskCancellationRegistration =
        FileTaskCancellationRegistration.NONE

    companion object {
        val NONE = FileTaskCancellationToken { }
    }
}

fun interface FileTaskCancellationRegistration {
    fun dispose()

    companion object {
        val NONE = FileTaskCancellationRegistration { }
    }
}

class FileTaskCancellationSource(
    private val additionalCancellationCheck: () -> Boolean = { false }
) {
    private val lock = Any()
    private val listeners = linkedMapOf<Long, () -> Unit>()
    private var nextListenerId = 0L

    @Volatile
    private var cancelled = false

    val token: FileTaskCancellationToken = object : FileTaskCancellationToken {
        override fun throwIfCancelled() {
            if (cancelled || additionalCancellationCheck()) throw FileTaskCancelledException()
        }

        override fun invokeOnCancellation(listener: () -> Unit): FileTaskCancellationRegistration =
            register(listener)
    }

    fun cancel() {
        val callbacks = synchronized(lock) {
            if (cancelled) return
            cancelled = true
            listeners.values.toList().also { listeners.clear() }
        }
        callbacks.forEach(::notifyListener)
    }

    private fun register(listener: () -> Unit): FileTaskCancellationRegistration {
        var listenerId: Long? = null
        val notifyNow = synchronized(lock) {
            if (cancelled) {
                true
            } else {
                listenerId = nextListenerId++
                listeners[listenerId!!] = listener
                false
            }
        }
        if (notifyNow) notifyListener(listener)
        val id = listenerId ?: return FileTaskCancellationRegistration.NONE
        return FileTaskCancellationRegistration {
            synchronized(lock) { listeners.remove(id) }
        }
    }

    private fun notifyListener(listener: () -> Unit) {
        try {
            listener()
        } catch (_: Exception) {
            // One listener must not prevent the remaining cancellation notifications.
        }
    }
}
