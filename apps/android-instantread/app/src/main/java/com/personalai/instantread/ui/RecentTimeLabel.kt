package com.personalai.instantread.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 把最近打开时间转换成首页列表可读的短标签。
 *
 * 输入为本地时间戳，输出只用于展示，不参与排序或持久化。
 */
internal fun recentTimeLabel(
    lastOpenedAt: Long,
    now: Long = System.currentTimeMillis()
): String {
    val elapsed = (now - lastOpenedAt).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    if (minutes < 1L) return "刚刚"
    if (minutes < 60L) return "$minutes 分钟前"
    val hours = minutes / 60L
    if (hours < 24L) return "$hours 小时前"
    if (hours < 48L) return "昨天"
    val days = hours / 24L
    if (days < 7L) return "$days 天前"
    val pattern = if (yearOf(lastOpenedAt) == yearOf(now)) "M月d日" else "yyyy年M月d日"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(lastOpenedAt))
}

private fun yearOf(time: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)
}
