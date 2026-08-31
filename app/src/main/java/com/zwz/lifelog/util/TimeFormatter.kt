package com.zwz.lifelog.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatter {

    private const val DAY = 86_400_000L

    private fun pattern(p: String) = SimpleDateFormat(p, Locale.CHINA)

    /** 2026年8月31日 14:30 */
    fun full(timestamp: Long): String =
        pattern("yyyy年M月d日 HH:mm").format(Date(timestamp))

    /** 今年内：8月31日 14:30；跨年：2025年8月31日 */
    fun short(timestamp: Long): String {
        val cal = Calendar.getInstance()
        val nowYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = timestamp
        return if (cal.get(Calendar.YEAR) == nowYear) {
            pattern("M月d日 HH:mm").format(Date(timestamp))
        } else {
            pattern("yyyy年M月d日").format(Date(timestamp))
        }
    }

    fun dateOnly(timestamp: Long): String = pattern("yyyy年M月d日").format(Date(timestamp))

    fun monthKey(timestamp: Long): String = pattern("yyyy年M月").format(Date(timestamp))

    /** 把毫秒差说成人话：45 天 / 3 个月（95 天） / 1 年 1 个月 */
    fun duration(millis: Long): String {
        val d = (millis / DAY).toInt().coerceAtLeast(0)
        return when {
            d < 1 -> "不到 1 天"
            d < 60 -> "$d 天"
            d < 365 -> {
                val months = (d / 30.44).toInt()
                "$months 个月（$d 天）"
            }
            else -> {
                val y = d / 365
                val rest = d % 365
                if (rest > 30) "$y 年 ${(rest / 30.44).toInt()} 个月" else "$y 年"
            }
        }
    }

    /** 距今天数的人话版本，用于卡片副标题 */
    fun daysAgo(days: Int): String = when (days) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "前天"
        else -> "$days 天前"
    }

    /** 把日期时间转成可显示的「距今 + 具体日期」 */
    fun agoWithDate(timestamp: Long): String {
        val d = ((System.currentTimeMillis() - timestamp) / DAY).toInt().coerceAtLeast(0)
        return "${daysAgo(d)} · ${short(timestamp)}"
    }
}
