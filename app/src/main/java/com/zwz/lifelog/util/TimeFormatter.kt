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

    /**
     * 今年内：8月31日 14:30；跨年：2025年8月31日。
     *
     * @param now 判断「是否今年」的基准，默认取系统时间。
     *            显式传入是为了让同一屏上的所有文案用同一个 now，
     *            否则跨年那一刻各算各的，会出现半屏去年半屏今年的割裂显示。
     */
    fun short(
        timestamp: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): String {
        val z = at(timestamp, zone)
        return if (z.year == Instant.ofEpochMilli(now).atZone(zone).year) {
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

    // ---------- 距今 ----------

    /**
     * 距今的自适应表达：刚刚 / 23 分钟前 / 8 小时前 / 昨天 / 前天 / 5 天前。
     *
     * 粒度随时长自动切换，因为不同事件的节奏差了两个数量级：
     *
     * | 事件 | 间隔 | 只显示「今天」 | 显示小时 |
     * |------|------|----------------|----------|
     * | 吃药 | 8 小时 | 等于没说，不知道该不该吃了 | 一眼看出还有多久 |
     * | 理发 | 35 天 | 够用 | 没必要 |
     *
     * **24 小时内一律用小时**，即便跨了自然日：
     * 昨晚 23:00 记的事，今晚 22:00 看是「23 小时前」，
     * 比「昨天」精确得多。超过 24 小时才切回日历口径
     * （[DayDiff.calendarDays]），避免「35 小时」这种反直觉的说法。
     *
     * @param now 便于测试固定时间、也便于 UI 用统一的 tick 渲染，默认取系统时间。
     */
    fun agoUnit(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "$minutes 分钟前"
        val hours = diff / 3_600_000L
        if (hours < 24) return "$hours 小时前"
        return daysAgo(DayDiff.calendarDays(timestamp, now, zone))
    }

    /**
     * [agoUnit] 的短版，供 chip、小组件等窄空间使用。
     *
     * 省掉「前」字和空格：横向排列时「8小时」比「8 小时前」省一半宽度，
     * 而这里的语义由位置已经表达清楚了。
     */
    fun agoCompact(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "${minutes}分"
        val hours = diff / 3_600_000L
        if (hours < 24) return "${hours}小时"
        return when (val d = DayDiff.calendarDays(timestamp, now, zone)) {
            0 -> "今天"
            1 -> "昨天"
            2 -> "前天"
            else -> "${d}天"
        }
    }

    /**
     * 把日期时间转成可显示的「距今 + 具体日期」，例如「前天 · 9月2日 22:00」。
     *
     * 曾经这里是 `(now - timestamp) / DAY` 整除，
     * 导致 9/2 22:00 的记录在 9/4 21:59 之前一直显示「昨天」——
     * 因为 35 小时凑不满两个 24 小时。改走 [DayDiff.calendarDays] 修掉。
     * 现在再往前一步，24 小时内改用 [agoUnit] 的小时粒度。
     *
     * @param now 便于测试时固定「当前时间」，默认取系统时间。
     */
    fun agoWithDate(
        timestamp: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): String = "${agoUnit(timestamp, now, zone)} · ${short(timestamp, zone, now)}"
}
