package com.personalai.instantread.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportCleanupCopyTest {
    @Test
    fun deletedMessageDistinguishesQueuedCleanup() {
        assertEquals(
            "已删除：示例.html；应用内副本和缓存已进入清理队列。",
            ImportCleanupCopy.deletedMessage("示例.html", cleanupScheduled = true)
        )
    }

    @Test
    fun deletedMessageExplainsRetryWhenCleanupSchedulingFails() {
        assertEquals(
            "已删除：示例.html；应用内副本或缓存未能清理，将在下次启动时重试。",
            ImportCleanupCopy.deletedMessage("示例.html", cleanupScheduled = false)
        )
    }

    @Test
    fun cacheClearFailureKeepsSourceFilesExplicitlySafe() {
        assertEquals(
            "清理 ZIP 缓存失败，请重试；原始文件不会被删除。",
            ImportCleanupCopy.zipCacheClearFailureMessage()
        )
    }
}
