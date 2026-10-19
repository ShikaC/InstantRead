package com.personalai.instantread.io

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object ZipPreviewCacheCoordinator {
    data class Lease internal constructor(
        internal val rootKey: String,
        internal val importId: String,
        internal val rootGeneration: Long
    )

    class PendingFinalization internal constructor(internal val key: String) {
        private val completed = CountDownLatch(1)

        internal fun await() = completed.await()
        internal fun await(timeoutMillis: Long): Boolean =
            completed.await(timeoutMillis, TimeUnit.MILLISECONDS)
        internal fun complete() = completed.countDown()
    }

    private val monitor = Any()
    private val invalidatedImports = mutableSetOf<String>()
    private val rootGenerations = mutableMapOf<String, Long>()
    private val pendingFinalizations = mutableMapOf<String, PendingFinalization>()

    fun lease(cacheRoot: File, importId: String): Lease = synchronized(monitor) {
        val rootKey = rootKey(cacheRoot)
        Lease(rootKey, importId, rootGenerations[rootKey] ?: 0L)
    }

    fun ensureActive(lease: Lease) {
        synchronized(monitor) {
            if (isInvalidatedLocked(lease)) {
                throw FileTaskCancelledException()
            }
        }
    }

    fun <T> withActiveCache(lease: Lease, action: () -> T): T {
        return synchronized(monitor) {
            if (isInvalidatedLocked(lease)) {
                throw FileTaskCancelledException()
            }
            action()
        }
    }

    fun <T> withCacheState(
        lease: Lease,
        action: (invalidated: Boolean) -> T
    ): T = synchronized(monitor) {
        action(isInvalidatedLocked(lease))
    }

    fun invalidate(cacheRoot: File, importId: String) {
        synchronized(monitor) {
            invalidatedImports += key(cacheRoot, importId)
        }
    }

    fun advanceRootGeneration(cacheRoot: File) {
        synchronized(monitor) {
            val rootKey = rootKey(cacheRoot)
            rootGenerations[rootKey] = (rootGenerations[rootKey] ?: 0L) + 1L
        }
    }

    fun beginFinalization(lease: Lease): PendingFinalization = synchronized(monitor) {
        val key = key(lease.rootKey, lease.importId)
        check(key !in pendingFinalizations) { "ZIP cache finalization already pending" }
        PendingFinalization(key).also { pendingFinalizations[key] = it }
    }

    fun beginRootFinalization(cacheRoot: File): PendingFinalization? = synchronized(monitor) {
        val key = rootFinalizationKey(rootKey(cacheRoot))
        if (key in pendingFinalizations) return@synchronized null
        PendingFinalization(key).also { pendingFinalizations[key] = it }
    }

    fun completeFinalization(finalization: PendingFinalization) {
        synchronized(monitor) {
            if (pendingFinalizations[finalization.key] === finalization) {
                pendingFinalizations.remove(finalization.key)
            }
            finalization.complete()
        }
    }

    fun awaitPendingFinalization(cacheRoot: File, importId: String) {
        val rootKey = rootKey(cacheRoot)
        val rootFinalizationKey = rootFinalizationKey(rootKey)
        val importKey = key(rootKey, importId)
        while (true) {
            val pending = synchronized(monitor) {
                pendingFinalizations[rootFinalizationKey] ?: pendingFinalizations[importKey]
            } ?: break
            pending.await()
        }
        if (hasPendingRootDeletion(cacheRoot)) {
            throw FileTaskCancelledException()
        }
    }

    fun awaitPendingRootFinalization(
        cacheRoot: File,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ) {
        val key = rootFinalizationKey(rootKey(cacheRoot))
        while (true) {
            val pending = synchronized(monitor) { pendingFinalizations[key] } ?: return
            cancellationToken.throwIfCancelled()
            pending.await(CANCELLATION_POLL_MILLIS)
        }
    }

    fun hasPendingRootDeletion(cacheRoot: File): Boolean =
        cacheRoot.parentFile?.listFiles().orEmpty().any { path ->
            path.name.startsWith("${cacheRoot.name}.delete-pending-")
        }

    fun isInvalidated(lease: Lease): Boolean = synchronized(monitor) {
        isInvalidatedLocked(lease)
    }

    private fun isInvalidatedLocked(lease: Lease): Boolean =
        key(lease.rootKey, lease.importId) in invalidatedImports ||
            (rootGenerations[lease.rootKey] ?: 0L) != lease.rootGeneration

    private fun key(cacheRoot: File, importId: String): String =
        key(rootKey(cacheRoot), importId)

    private fun key(rootKey: String, importId: String): String = "$rootKey\u0000$importId"

    private fun rootFinalizationKey(rootKey: String): String = "$rootKey\u0000*"

    private fun rootKey(cacheRoot: File): String = cacheRoot.absoluteFile.path

    private const val CANCELLATION_POLL_MILLIS = 50L
}
