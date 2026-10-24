package com.personalai.instantread.ui

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTimeLabelTest {
    @Test
    fun recentMomentsUseShortRelativeLabels() {
        val now = localTime(2026, Calendar.OCTOBER, 19, 12)

        assertEquals("刚刚", recentTimeLabel(now - 30_000L, now))
        assertEquals("5 分钟前", recentTimeLabel(now - 5 * 60_000L, now))
        assertEquals("3 小时前", recentTimeLabel(now - 3 * 3_600_000L, now))
        assertEquals("昨天", recentTimeLabel(now - 30 * 3_600_000L, now))
        assertEquals("3 天前", recentTimeLabel(now - 3 * 24 * 3_600_000L, now))
    }

    @Test
    fun olderEntriesUseCalendarDates() {
        val now = localTime(2026, Calendar.OCTOBER, 19, 12)

        assertEquals("10月1日", recentTimeLabel(localTime(2026, Calendar.OCTOBER, 1, 9), now))
        assertEquals("2025年12月31日", recentTimeLabel(localTime(2025, Calendar.DECEMBER, 31, 9), now))
    }

    @Test
    fun futureTimestampsDoNotProduceNegativeLabels() {
        val now = localTime(2026, Calendar.OCTOBER, 19, 12)

        assertEquals("刚刚", recentTimeLabel(now + 60_000L, now))
    }

    private fun localTime(year: Int, month: Int, day: Int, hour: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
    }
}
