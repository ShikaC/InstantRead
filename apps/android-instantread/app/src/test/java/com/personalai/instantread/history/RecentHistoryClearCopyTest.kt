package com.personalai.instantread.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentHistoryClearCopyTest {
    @Test
    fun resultFlagsCleanupFailureOnlyWhenSomeCopiesWereNotScheduled() {
        assertFalse(ClearRecentHistoryResult(clearedCount = 2, cleanupScheduledCount = 2).hasCleanupFailure)
        assertTrue(ClearRecentHistoryResult(clearedCount = 2, cleanupScheduledCount = 1).hasCleanupFailure)
        assertFalse(ClearRecentHistoryResult(clearedCount = 0, cleanupScheduledCount = 0).hasCleanupFailure)
    }

    @Test
    fun clearedMessageExplainsThatFavoritesAndOriginalFilesSurvive() {
        val message = RecentHistoryClearCopy.clearedMessage(ClearRecentHistoryResult(3, 3))

        assertTrue(message.contains("已清空 3 条未收藏记录"))
        assertTrue(message.contains("已收藏"))
        assertTrue(message.contains("原始文件"))
    }

    @Test
    fun clearedMessageReportsPendingCleanupWithoutClaimingSuccess() {
        val message = RecentHistoryClearCopy.clearedMessage(ClearRecentHistoryResult(2, 1))

        assertTrue(message.contains("已清空 2 条未收藏记录"))
        assertTrue(message.contains("下次启动"))
        assertFalse(message.contains("未受影响"))
    }

    @Test
    fun clearedMessageOnEmptyHistorySaysNothingWasCleared() {
        assertEquals(
            "没有可清空的记录；已收藏的文件会保留。",
            RecentHistoryClearCopy.clearedMessage(ClearRecentHistoryResult(0, 0))
        )
    }
}
