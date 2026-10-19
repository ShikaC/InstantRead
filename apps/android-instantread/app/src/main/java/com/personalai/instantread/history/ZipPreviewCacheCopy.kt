package com.personalai.instantread.history

object ZipPreviewCacheCopy {
    fun summary(stats: ZipPreviewCacheStats): String {
        return if (stats.isEmpty) {
            "ZIP 解包缓存：暂无"
        } else {
            "ZIP 解包缓存：${stats.importDirectories} 个目录，${stats.files} 个文件，${formatBytes(stats.bytes)}"
        }
    }

    fun clearedMessage(stats: ZipPreviewCacheStats): String {
        return "已清理 ZIP 解包缓存：${stats.importDirectories} 个目录，${stats.files} 个文件，${formatBytes(stats.bytes)}。"
    }

    fun clearResultMessage(result: ZipCacheClearResult): String {
        return when (result.status) {
            ZipCacheClearStatus.CLEARED -> clearedMessage(result.stats)
            ZipCacheClearStatus.SCHEDULED -> "已安排清理 ZIP 解包缓存：${result.stats.importDirectories} 个目录，${result.stats.files} 个文件，${formatBytes(result.stats.bytes)}；后台清理完成后缓存会消失。"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return "$bytes B"
    }
}
