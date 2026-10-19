package com.personalai.instantread.tasks

import com.personalai.instantread.history.ImportCleanup
import com.personalai.instantread.history.ZipCacheClearResult
import com.personalai.instantread.history.ZipPreviewCacheStats
import com.personalai.instantread.io.BackgroundFileDeletionQueue
import com.personalai.instantread.io.FileTaskCancellationToken
import java.io.File

class ZipCacheSettingsController(
    private val taskController: ForegroundFileTaskController,
    private val cacheDir: File,
    private val scheduleDeletion: (File) -> Unit = BackgroundFileDeletionQueue::schedule,
    private val detachRootBeforeDeletion: Boolean = true
) {
    fun load(onCompleted: (ZipPreviewCacheStats) -> Unit) {
        taskController.start<ZipPreviewCacheStats, Unit>(
            work = { token, _ -> ImportCleanup.zipPreviewCacheStats(cacheDir, token) },
            onProgress = {},
            onSuccess = onCompleted,
            onFailure = { onCompleted(ZipPreviewCacheStats(0, 0, 0L)) }
        )
    }

    fun clear(
        onCompleted: (ZipCacheClearResult) -> Unit,
        onFailure: (Throwable) -> Unit = {}
    ) {
        taskController.start<ZipCacheClearResult, Unit>(
            work = { token, _ ->
                ImportCleanup.clearZipPreviewCache(
                    cacheDir = cacheDir,
                    cancellationToken = token,
                    detachRootBeforeDeletion = detachRootBeforeDeletion,
                    scheduleDeletion = scheduleDeletion
                )
            },
            onProgress = {},
            onSuccess = onCompleted,
            onFailure = onFailure
        )
    }
}
