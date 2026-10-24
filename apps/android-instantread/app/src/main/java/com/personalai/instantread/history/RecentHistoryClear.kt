package com.personalai.instantread.history

import java.io.File

data class ClearRecentHistoryResult(
    val clearedCount: Int,
    val cleanupScheduledCount: Int
) {
    val hasCleanupFailure: Boolean
        get() = cleanupScheduledCount < clearedCount
}

/**
 * 一键清空未收藏记录。
 *
 * 只删除 `favorite = false` 的记录，并复用单条删除的清理路径移除应用内副本与对应缓存；
 * 已收藏记录和手机里的原始文件都不受影响。返回计数用于区分「没有可清空记录」和「清理未全部完成」。
 */
class RecentHistoryClearController(
    private val repository: PreviewHistoryRepository,
    private val filesDir: File,
    private val cacheDir: File,
    private val deleteImportAndCache: (String, File, File) -> List<File> = { id, files, cache ->
        ImportCleanup.deleteImportAndCache(id, files, cache)
    }
) {
    fun clearNotFavorite(): ClearRecentHistoryResult {
        var clearedCount = 0
        var cleanupScheduledCount = 0
        repository.listRecent().filterNot { it.favorite }.forEach { history ->
            if (repository.delete(history.id) == null) return@forEach
            clearedCount += 1
            if (runCatching { deleteImportAndCache(history.id, filesDir, cacheDir) }.isSuccess) {
                cleanupScheduledCount += 1
            }
        }
        return ClearRecentHistoryResult(clearedCount, cleanupScheduledCount)
    }
}

object RecentHistoryClearCopy {
    fun clearedMessage(result: ClearRecentHistoryResult): String = when {
        result.clearedCount == 0 -> "没有可清空的记录；已收藏的文件会保留。"
        result.hasCleanupFailure -> "已清空 ${result.clearedCount} 条未收藏记录；部分应用内副本或缓存未能清理，" +
            "将在下次启动时重试。已收藏的文件已保留。"
        else -> "已清空 ${result.clearedCount} 条未收藏记录；已收藏的文件和手机里的原始文件未受影响。"
    }
}
