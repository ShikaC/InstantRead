package com.personalai.instantread.history

import com.personalai.instantread.io.BackgroundFileDeletionQueue
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.ZipPreviewCacheCoordinator
import com.personalai.instantread.io.isSafeImportId
import java.io.File
import java.io.IOException
import java.util.UUID

data class ZipPreviewCacheStats(
    val importDirectories: Int,
    val files: Int,
    val bytes: Long
) {
    val isEmpty: Boolean
        get() = importDirectories == 0 && files == 0 && bytes == 0L
}

enum class ZipCacheClearStatus {
    CLEARED,
    SCHEDULED
}

data class ZipCacheClearResult(
    val stats: ZipPreviewCacheStats,
    val status: ZipCacheClearStatus
)

object ImportCleanup {
    fun deleteImportAndCache(
        importId: String,
        filesDir: File,
        cacheDir: File,
        scheduleDeletion: (File) -> Unit = BackgroundFileDeletionQueue::schedule
    ): List<File> {
        if (!importId.isSafeImportId()) {
            throw IOException("Refusing to delete import with unsafe id: $importId")
        }
        val deletionId = UUID.randomUUID().toString()
        val importsRoot = File(filesDir, "imports")
        val detachedPaths = mutableListOf<File>()
        detachAndSchedule(File(importsRoot, importId), deletionId)?.let(detachedPaths::add)
        detachAndSchedule(File(importsRoot, "$importId.partial"), deletionId)?.let(detachedPaths::add)

        val zipRoot = zipPreviewRoot(cacheDir)
        ZipPreviewCacheCoordinator.invalidate(zipRoot, importId)
        detachedPaths += zipRoot.listFiles().orEmpty()
            .filter { cachePath ->
                cachePath.name == importId ||
                    cachePath.name.startsWith("$importId.partial-") ||
                    cachePath.name.startsWith("$importId.previous-") ||
                    cachePath.name.startsWith("$importId.discarded-") ||
                    cachePath.name.startsWith("$importId.deleted-")
            }
            .mapNotNull { path ->
                detachAndSchedule(path, deletionId)
            }

        detachedPaths.forEach(scheduleDeletion)
        return detachedPaths
    }

    fun clearZipPreviewCache(
        cacheDir: File,
        knownStats: ZipPreviewCacheStats? = null,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE,
        detachRootBeforeDeletion: Boolean = true,
        scheduleDeletion: (File) -> Unit = BackgroundFileDeletionQueue::schedule,
        renamePath: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
        deleteDirectory: (File) -> Boolean = File::deleteRecursively,
        scheduleFinalization: (() -> Unit) -> Unit = BackgroundFileDeletionQueue::execute
    ): ZipCacheClearResult {
        cancellationToken.throwIfCancelled()
        val stats = knownStats ?: zipPreviewCacheStats(cacheDir, cancellationToken)
        val root = zipPreviewRoot(cacheDir)
        val finalization = acquireRootFinalization(root, cancellationToken)
        var finalizationScheduled = false
        try {
            cancellationToken.throwIfCancelled()
            ZipPreviewCacheCoordinator.advanceRootGeneration(root)
            if (!root.exists()) {
                if (deletePendingRootMarkers(cacheDir, deleteDirectory)) {
                    return ZipCacheClearResult(stats, ZipCacheClearStatus.CLEARED)
                }
                schedulePendingRootDeletion(
                    cacheDir = cacheDir,
                    root = root,
                    finalization = finalization,
                    deleteDirectory = deleteDirectory,
                    scheduleFinalization = scheduleFinalization
                )
                finalizationScheduled = true
                return ZipCacheClearResult(stats, ZipCacheClearStatus.SCHEDULED)
            }

            val detached = File(cacheDir, "${root.name}.deleted-${UUID.randomUUID()}")
            if (detachRootBeforeDeletion && renamePath(root, detached) && detached.exists() && !root.exists()) {
                scheduleDeletion(detached)
                if (deletePendingRootMarkers(cacheDir, deleteDirectory)) {
                    return ZipCacheClearResult(stats, ZipCacheClearStatus.SCHEDULED)
                }
                schedulePendingRootDeletion(
                    cacheDir = cacheDir,
                    root = root,
                    finalization = finalization,
                    deleteDirectory = deleteDirectory,
                    scheduleFinalization = scheduleFinalization
                )
                finalizationScheduled = true
                return ZipCacheClearResult(stats, ZipCacheClearStatus.SCHEDULED)
            }

            val marker = File(cacheDir, "${root.name}.delete-pending-${UUID.randomUUID()}")
            if (!marker.mkdirs() && !marker.isDirectory) {
                throw IOException("Unable to persist ZIP cache deletion intent")
            }
            if (
                deleteWithRetries(root, deleteDirectory) &&
                deletePendingRootMarkers(cacheDir, deleteDirectory)
            ) {
                return ZipCacheClearResult(stats, ZipCacheClearStatus.CLEARED)
            }

            schedulePendingRootDeletion(
                cacheDir = cacheDir,
                root = root,
                finalization = finalization,
                deleteDirectory = deleteDirectory,
                scheduleFinalization = scheduleFinalization
            )
            finalizationScheduled = true
            return ZipCacheClearResult(stats, ZipCacheClearStatus.SCHEDULED)
        } finally {
            if (!finalizationScheduled) {
                ZipPreviewCacheCoordinator.completeFinalization(finalization)
            }
        }
    }

    fun scheduleDetachedCleanup(
        filesDir: File,
        cacheDir: File,
        activeImportIds: Set<String> = emptySet(),
        deleteDirectory: (File) -> Boolean = File::deleteRecursively,
        scheduleFinalization: (() -> Unit) -> Unit = BackgroundFileDeletionQueue::execute,
        scheduleDeletion: (File) -> Unit = BackgroundFileDeletionQueue::schedule
    ) {
        val importsRoot = File(filesDir, "imports")
        importsRoot.listFiles().orEmpty()
            .filter { it.name !in activeImportIds }
            .forEach(scheduleDeletion)

        val zipRoot = zipPreviewRoot(cacheDir)
        val pendingRootMarkers = pendingRootDeletionMarkers(cacheDir)
        if (pendingRootMarkers.isNotEmpty()) {
            if (zipRoot.exists()) {
                schedulePendingRootDeletion(
                    cacheDir = cacheDir,
                    root = zipRoot,
                    deleteDirectory = deleteDirectory,
                    scheduleFinalization = scheduleFinalization
                )
            } else {
                pendingRootMarkers.forEach(scheduleDeletion)
            }
        } else {
            zipRoot.listFiles().orEmpty()
                .filter { path ->
                    path.name !in activeImportIds &&
                        activeImportIds.none { activeId -> path.name.startsWith("$activeId.previous-") }
                }
                .forEach(scheduleDeletion)
        }
        cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("zip-preview.deleted-") }
            .forEach(scheduleDeletion)
    }

    fun zipPreviewCacheStats(
        cacheDir: File,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): ZipPreviewCacheStats {
        cancellationToken.throwIfCancelled()
        val root = zipPreviewRoot(cacheDir)
        ZipPreviewCacheCoordinator.awaitPendingRootFinalization(root, cancellationToken)
        if (ZipPreviewCacheCoordinator.hasPendingRootDeletion(root)) {
            cancellationToken.throwIfCancelled()
            return ZipPreviewCacheStats(importDirectories = 0, files = 0, bytes = 0L)
        }
        if (!root.exists()) {
            cancellationToken.throwIfCancelled()
            return ZipPreviewCacheStats(importDirectories = 0, files = 0, bytes = 0L)
        }

        val importDirectories = root.listFiles()
            ?.count { it.isDirectory }
            ?: 0
        var fileCount = 0
        var totalBytes = 0L
        root.walkTopDown()
            .forEach { file ->
                cancellationToken.throwIfCancelled()
                if (!file.isFile) return@forEach
                fileCount += 1
                totalBytes += file.length()
            }
        cancellationToken.throwIfCancelled()

        return ZipPreviewCacheStats(
            importDirectories = importDirectories,
            files = fileCount,
            bytes = totalBytes
        )
    }

    private fun zipPreviewRoot(cacheDir: File): File {
        return File(cacheDir, "zip-preview")
    }

    private fun acquireRootFinalization(
        root: File,
        cancellationToken: FileTaskCancellationToken
    ): ZipPreviewCacheCoordinator.PendingFinalization {
        while (true) {
            cancellationToken.throwIfCancelled()
            ZipPreviewCacheCoordinator.beginRootFinalization(root)?.let { return it }
            ZipPreviewCacheCoordinator.awaitPendingRootFinalization(root, cancellationToken)
        }
    }

    private fun schedulePendingRootDeletion(
        cacheDir: File,
        root: File,
        finalization: ZipPreviewCacheCoordinator.PendingFinalization? = null,
        deleteDirectory: (File) -> Boolean,
        scheduleFinalization: (() -> Unit) -> Unit
    ) {
        val ownedFinalization = finalization
            ?: ZipPreviewCacheCoordinator.beginRootFinalization(root)
            ?: return
        if (finalization == null) {
            ZipPreviewCacheCoordinator.advanceRootGeneration(root)
        }
        try {
            scheduleFinalization {
                try {
                    if (deleteWithRetries(root, deleteDirectory)) {
                        deletePendingRootMarkers(cacheDir, deleteDirectory)
                    }
                } finally {
                    ZipPreviewCacheCoordinator.completeFinalization(ownedFinalization)
                }
            }
        } catch (error: Throwable) {
            ZipPreviewCacheCoordinator.completeFinalization(ownedFinalization)
            throw error
        }
    }

    private fun deleteWithRetries(
        path: File,
        deleteDirectory: (File) -> Boolean
    ): Boolean {
        repeat(DELETE_ATTEMPTS) {
            if (!path.exists()) return true
            val deleted = try {
                deleteDirectory(path)
            } catch (_: Exception) {
                false
            }
            if (deleted || !path.exists()) return true
        }
        return !path.exists()
    }

    private fun deletePendingRootMarkers(
        cacheDir: File,
        deleteDirectory: (File) -> Boolean
    ): Boolean {
        var allDeleted = true
        pendingRootDeletionMarkers(cacheDir).forEach { marker ->
            if (!deleteWithRetries(marker, deleteDirectory)) allDeleted = false
        }
        return allDeleted
    }

    private fun pendingRootDeletionMarkers(cacheDir: File): List<File> =
        cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("zip-preview.delete-pending-") }

    private fun detachAndSchedule(
        path: File,
        deletionId: String
    ): File? {
        if (!path.exists()) return null
        val detached = File(path.parentFile, "${path.name}.deleted-$deletionId")
        val deletionTarget = if (path.renameTo(detached) && detached.exists() && !path.exists()) {
            detached
        } else {
            path
        }
        return deletionTarget
    }

    private const val DELETE_ATTEMPTS = 3
}
