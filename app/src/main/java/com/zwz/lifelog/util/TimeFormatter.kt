package com.zwz.lifelog.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatter {

    private const val DAY = 86_400_000L

    // DateTimeFormatter 不可变且线程安全，可以放心缓存复用；
    // SimpleDateFormat 有状态，原来每次格式化都新建一个，纯属浪费。
    private val FMT_FULL = pattern("yyyy年M月d日 HH:mm")
    private val FMT_SHORT = pattern("M月d日 HH:mm")
    private val FMT_DATE = pattern("yyyy年M月d日")
    private val FMT_MONTH = pattern("yyyy年M月")

    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, Locale.CHINA)

    private fun at(timestamp: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(timestamp).atZone(zone)

    /** 2026年8月31日 14:30 */
    fun full(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        FMT_FULL.format(at(timestamp, zone))

    /** 今年内：8月31日 14:30；跨年：2025年8月31日 */
    fun short(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val z = at(timestamp, zone)
        return if (z.year == ZonedDateTime.now(zone).year) {
            FMT_SHORT.format(z)
        } else {
            FMT_DATE.format(z)
        }
    }

    fun dateOnly(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        FMT_DATE.format(at(timestamp, zone))

    fun monthKey(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        FMT_MONTH.format(at(timestamp, zone))

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

    /**
     * 几天前的人话版本，用于卡片副标题。
     *
     * ⚠️ 入参必须是**自然日差**（[DayDiff.calendarDays]），
     * 传入流逝整天数会让「前天」显示成「昨天」。
     */
    fun daysAgo(days: Int): String = when (days) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "前天"
        else -> "$days 天前"
    }

    /**
     * 把日期时间转成可显示的「距今 + 具体日期」，例如「前天 · 9月2日 22:00」。
     *
     * 曾经这里是 `(now - timestamp) / DAY` 整除，
     * 导致 9/2 22:00 的记录在 9/4 21:59 之前一直显示「昨天」——
     * 因为 35 小时凑不满两个 24 小时。改走 [DayDiff.calendarDays] 修掉。
     *
     * @param now 便于测试时固定「当前时间」，默认取系统时间。
     */
    fun agoWithDate(
        timestamp: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): String {
        val d = DayDiff.calendarDays(timestamp, now, zone)
        return "${daysAgo(d)} · ${short(timestamp, zone)}"
    }
}
