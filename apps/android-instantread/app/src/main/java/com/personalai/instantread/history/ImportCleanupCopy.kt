package com.personalai.instantread.history

object ImportCleanupCopy {
    fun deletedMessage(displayTitle: String, cleanupScheduled: Boolean): String {
        val cleanup = if (cleanupScheduled) {
            "应用内副本和缓存已进入清理队列"
        } else {
            "应用内副本或缓存未能清理，将在下次启动时重试"
        }
        return "已删除：$displayTitle；$cleanup。"
    }

    fun zipCacheClearFailureMessage(): String =
        "清理 ZIP 缓存失败，请重试；原始文件不会被删除。"
}
