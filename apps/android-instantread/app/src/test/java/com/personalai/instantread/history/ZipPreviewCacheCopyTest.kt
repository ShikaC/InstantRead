package com.personalai.instantread.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ZipPreviewCacheCopyTest {
    @Test
    fun emptySummarySaysThereIsNoZipPreviewCache() {
        val summary = ZipPreviewCacheCopy.summary(
            ZipPreviewCacheStats(importDirectories = 0, files = 0, bytes = 0L)
        )

        assertEquals("ZIP 解包缓存：暂无", summary)
    }

    @Test
    fun summaryShowsDirectoriesFilesAndBytes() {
        val summary = ZipPreviewCacheCopy.summary(
            ZipPreviewCacheStats(importDirectories = 2, files = 3, bytes = 128L)
        )

        assertEquals("ZIP 解包缓存：2 个目录，3 个文件，128 B", summary)
    }

    @Test
    fun clearedMessageShowsClearedStats() {
        val message = ZipPreviewCacheCopy.clearedMessage(
            ZipPreviewCacheStats(importDirectories = 2, files = 3, bytes = 128L)
        )

        assertEquals("已清理 ZIP 解包缓存：2 个目录，3 个文件，128 B。", message)
    }

    @Test
    fun clearResultMessageOnlyClaimsClearedAfterSynchronousDeletion() {
        val message = ZipPreviewCacheCopy.clearResultMessage(
            ZipCacheClearResult(
                stats = ZipPreviewCacheStats(importDirectories = 2, files = 3, bytes = 128L),
                status = ZipCacheClearStatus.CLEARED
            )
        )

        assertEquals("已清理 ZIP 解包缓存：2 个目录，3 个文件，128 B。", message)
    }

    @Test
    fun clearResultMessageReportsScheduledDeletionWithoutClaimingCompletion() {
        val message = ZipPreviewCacheCopy.clearResultMessage(
            ZipCacheClearResult(
                stats = ZipPreviewCacheStats(importDirectories = 2, files = 3, bytes = 128L),
                status = ZipCacheClearStatus.SCHEDULED
            )
        )

        assertEquals(
            "已安排清理 ZIP 解包缓存：2 个目录，3 个文件，128 B；后台清理完成后缓存会消失。",
            message
        )
    }
}
