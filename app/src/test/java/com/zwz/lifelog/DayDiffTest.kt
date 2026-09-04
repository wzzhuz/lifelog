package com.zwz.lifelog

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.domain.usecase.StatusCalculator
import com.zwz.lifelog.util.DayDiff
import com.zwz.lifelog.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 「几天前」的回归测试。
 *
 * 起因：9/2 22:00 的记录到 9/4 还显示成「昨天」。
 * 旧实现用 `(now - timestamp) / 86_400_000` 整除，
 * 35 小时凑不满两个 24 小时，于是被算成 1 天。
 *
 * 现在拆成两个口径：显示走自然日 [DayDiff.calendarDays]，
 * 新鲜度走真实时长 [DayDiff.elapsedDays]，这里两边都验。
 */
class DayDiffTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun ts(text: String, zone: ZoneId = shanghai): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    // ---------- 原始 bug ----------

    @Test
    fun `9月2日22点的事到9月4日9点是前天`() {
        val recorded = ts("2026-09-02T22:00")
        val now = ts("2026-09-04T09:00")

        // 真实只过了 35 小时，但日期翻了两页
        assertEquals(2, DayDiff.calendarDays(recorded, now))
        assertEquals(1.4583333f, DayDiff.elapsedDays(recorded, now), 0.0001f)

        // 显示层：前天
        assertEquals("前天", TimeFormatter.daysAgo(DayDiff.calendarDays(recorded, now, shanghai)))
        // 跨年后副标题会带上年份，这里只固定住「距今」部分
        assertEquals(
            "前天",
            TimeFormatter.agoWithDate(recorded, shanghai, now).substringBefore(" · ")
        )
    }

    @Test
    fun `同一条记录的文案不再随查看时刻漂移`() {
        val recorded = ts("2026-09-02T22:00")
        // 旧实现下这两个时刻会给出不同答案（1 天 vs 2 天）
        assertEquals(2, DayDiff.calendarDays(recorded, ts("2026-09-04T00:30")))
        assertEquals(2, DayDiff.calendarDays(recorded, ts("2026-09-04T21:59")))
        assertEquals(2, DayDiff.calendarDays(recorded, ts("2026-09-04T22:00")))
    }

    // ---------- 自然日口径 ----------

    @Test
    fun `同一天内无论几点都是今天`() {
        assertEquals(0, DayDiff.calendarDays(ts("2026-09-04T01:00"), ts("2026-09-04T23:59")))
    }

    @Test
    fun `隔两小时但跨了午夜就算昨天`() {
        // 22 小时 vs 2 小时：旧实现前者算昨天、后者算今天，都不对
        assertEquals(1, DayDiff.calendarDays(ts("2026-09-03T23:00"), ts("2026-09-04T01:00")))
        assertEquals(1, DayDiff.calendarDays(ts("2026-09-03T22:00"), ts("2026-09-04T20:00")))
    }

    @Test
    fun `跨年仍然正确`() {
        assertEquals(1, DayDiff.calendarDays(ts("2025-12-31T23:59"), ts("2026-01-01T00:01")))
    }

    // ---------- 夏令时 ----------

    @Test
    fun `夏令时前进那天只有23小时仍算1天`() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-03-08 美东 02:00 直接跳到 03:00，这天只有 23 小时
        val before = ts("2026-03-07T22:00", newYork)
        val after = ts("2026-03-08T10:00", newYork)

        assertEquals(1, DayDiff.calendarDays(before, after, newYork))
        // 真实只过了 11 小时，不足半天——这正是两个口径要分开的原因
        assertEquals(0.4583333f, DayDiff.elapsedDays(before, after), 0.0001f)
    }

    @Test
    fun `夏令时回拨那天有25小时仍算1天`() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-11-01 美东 02:00 回拨到 01:00，这天有 25 小时
        val before = ts("2026-10-31T22:00", newYork)
        val after = ts("2026-11-01T10:00", newYork)

        assertEquals(1, DayDiff.calendarDays(before, after, newYork))
        assertEquals(0.5416667f, DayDiff.elapsedDays(before, after), 0.0001f)
    }

    // ---------- 边界 ----------

    @Test
    fun `未来时间戳按0处理`() {
        assertEquals(0, DayDiff.calendarDays(ts("2026-09-05T00:00"), ts("2026-09-04T09:00")))
        assertEquals(0f, DayDiff.elapsedDays(ts("2026-09-05T00:00"), ts("2026-09-04T09:00")), 0.0001f)
    }

    @Test
    fun `文案映射`() {
        assertEquals("今天", TimeFormatter.daysAgo(0))
        assertEquals("昨天", TimeFormatter.daysAgo(1))
        assertEquals("前天", TimeFormatter.daysAgo(2))
        assertEquals("5 天前", TimeFormatter.daysAgo(5))
    }

    // ---------- 两个口径在状态计算里各司其职 ----------

    @Test
    fun `显示用自然日而新鲜度用流逝时长`() {
        val event = Event(id = 1, name = "换床单", targetDays = 2)
        val records = listOf(Record(eventId = 1, timestamp = ts("2026-09-02T22:00")))
        val now = ts("2026-09-04T09:00")

        val status = StatusCalculator.compute(event, records, now)

        // 界面上写「2 天前 / 前天」
        assertEquals(2, status.daysAgo)
        // 但进度按 35 小时 ÷ 48 小时 = 0.73，还没到「快到了」的 0.75
        assertEquals(0.7291667f, status.ratio, 0.0001f)
        assertEquals(Freshness.FRESH, status.freshness)
    }

    @Test
    fun `没有记录时两个口径都是null`() {
        val status = StatusCalculator.compute(Event(id = 1, name = "理发"), emptyList())

        assertEquals(null, status.daysAgo)
        assertEquals(Freshness.NONE, status.freshness)
    }
}
