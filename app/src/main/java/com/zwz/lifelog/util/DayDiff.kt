package com.zwz.lifelog.util

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 「几天前」的两种算法。这两个值不是一回事，混用就是 bug。
 *
 * 以 `9/2 22:00 记录、9/4 09:00 查看` 为例（相隔 35 小时）：
 *
 * | 算法           | 结果      | 含义                     |
 * |----------------|-----------|--------------------------|
 * | [calendarDays] | 2（前天） | 翻过了几次日期           |
 * | [elapsedDays]  | 1.46      | 攒够了几个 24 小时        |
 *
 * 「昨天/前天」是日历概念，人看的是日期有没有翻页，必须用 [calendarDays]；
 * 「该不该做了」是流逝时长概念，用 [elapsedDays] 更平滑，
 * 不会因为差几个小时就把状态从「新鲜」跳到「该做了」。
 */
object DayDiff {

    private const val DAY_MILLIS = 86_400_000L

    /**
     * 两个时刻之间跨了几个自然日边界：同一天=0，昨天=1，前天=2。
     *
     * 天然免疫夏令时：[java.time.LocalDate] 只到日期、不带时分秒，
     * 一天是 23 小时还是 25 小时都照样算 1 天。
     * 若写成 `(to - from) / 86400000`，夏令时切换那几天会整整差一天。
     *
     * @param zone 默认设备当前时区，即按「当地日历」理解：
     *             跨时区旅行时，昨天在当地就是昨天，不会因为换了时区就变。
     * @return 未来时间戳（倒填填错了）按 0 处理，与旧行为一致。
     */
    fun calendarDays(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(from).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        ).toInt().coerceAtLeast(0)

    /**
     * 真实流逝了多少天（含小数），用于新鲜度比例。
     *
     * 不取整：35 小时 = 1.458 天，比先向下取整成 1 天更贴近真实状态。
     * 夏令时下这天可能只有 23 小时，但比例本来就该按真实时长算，不必修正。
     *
     * @return 未来时间戳按 0 处理。
     */
    fun elapsedDays(from: Long, to: Long): Float =
        (to - from).coerceAtLeast(0L) / DAY_MILLIS.toFloat()
}
