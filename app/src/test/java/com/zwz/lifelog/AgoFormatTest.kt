package com.zwz.lifelog

import com.zwz.lifelog.util.TimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 「距今」文案的粒度。
 *
 * 起因：吃药这类事件间隔只有几小时，界面却一律显示「今天」——
 * 等于什么都没说，用户看不出距离下一顿还有多久。
 * 现在 24 小时内改用小时，超过才切回日历口径。
 */
class AgoFormatTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private lateinit var originalZone: TimeZone

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    private fun ts(text: String, zone: ZoneId = shanghai): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** 相对某个基准时刻往前若干分钟。 */
    private fun minutesAgo(base: String, minutes: Long): Pair<Long, Long> {
        val now = ts(base)
        return (now - minutes * 60_000) to now
    }

    // ---------- 吃药场景 ----------

    @Test
    fun `8小时前不再显示今天`() {
        val (recorded, now) = minutesAgo("2026-09-05T16:00", 8 * 60)

        assertEquals("8 小时前", TimeFormatter.agoUnit(recorded, now, shanghai))
        assertEquals("8小时", TimeFormatter.agoCompact(recorded, now, shanghai))
    }

    @Test
    fun `不到1小时显示分钟`() {
        val (recorded, now) = minutesAgo("2026-09-05T16:00", 23)

        assertEquals("23 分钟前", TimeFormatter.agoUnit(recorded, now, shanghai))
        assertEquals("23分", TimeFormatter.agoCompact(recorded, now, shanghai))
    }

    @Test
    fun `不到1分钟显示刚刚`() {
        val now = ts("2026-09-05T16:00")

        assertEquals("刚刚", TimeFormatter.agoUnit(now - 30_000, now, shanghai))
        assertEquals("刚刚", TimeFormatter.agoCompact(now - 30_000, now, shanghai))
    }

    // ---------- 与日历口径的衔接 ----------

    @Test
    fun `24小时内即便跨了自然日也用小时`() {
        // 昨晚 23:00 记的，今晚 22:00 看：23 小时，但已经不是「今天」了
        val recorded = ts("2026-09-04T23:00")
        val now = ts("2026-09-05T22:00")

        assertEquals(1, com.zwz.lifelog.util.DayDiff.calendarDays(recorded, now, shanghai))
        assertEquals("23 小时前", TimeFormatter.agoUnit(recorded, now, shanghai))
    }

    @Test
    fun `超过24小时切回日历口径`() {
        val recorded = ts("2026-09-02T22:00")
        val now = ts("2026-09-04T09:00")

        // 35 小时：说「35 小时前」没人有概念，「前天」才是对的
        assertEquals("前天", TimeFormatter.agoUnit(recorded, now, shanghai))
        assertEquals("前天", TimeFormatter.agoCompact(recorded, now, shanghai))
    }

    @Test
    fun `整天边界`() {
        val recorded = ts("2026-09-04T10:00")
        // 差 1 毫秒不到 24 小时 —— 仍是小时
        assertEquals(
            "23 小时前",
            TimeFormatter.agoUnit(recorded, recorded + 24 * 3_600_000 - 1, shanghai)
        )
        // 满 24 小时 —— 切到天
        assertEquals(
            "昨天",
            TimeFormatter.agoUnit(recorded, recorded + 24 * 3_600_000, shanghai)
        )
    }

    // ---------- 短版与完整版的一致性 ----------

    @Test
    fun `短版只是省掉修饰词`() {
        val now = ts("2026-09-05T16:00")
        listOf(2L, 90L, 5 * 60L, 20 * 60L).forEach { minutes ->
            val recorded = now - minutes * 60_000
            val full = TimeFormatter.agoUnit(recorded, now, shanghai)
            val compact = TimeFormatter.agoCompact(recorded, now, shanghai)
            // 数字部分必须一致，只是文案长短不同
            assertEquals(
                "minutes=$minutes",
                full.filter { it.isDigit() },
                compact.filter { it.isDigit() }
            )
        }
    }

    @Test
    fun `未来时间戳按刚刚处理`() {
        val now = ts("2026-09-05T16:00")
        // 倒填填错了、或者设备时钟回拨
        assertEquals("刚刚", TimeFormatter.agoUnit(now + 3_600_000, now, shanghai))
        assertEquals("刚刚", TimeFormatter.agoCompact(now + 3_600_000, now, shanghai))
    }

    // ---------- 卡片副标题 ----------

    @Test
    fun `副标题带上小时粒度`() {
        val recorded = ts("2026-09-05T08:00")
        val now = ts("2026-09-05T16:00")

        val text = TimeFormatter.agoWithDate(recorded, shanghai, now)
        assertEquals("8 小时前 · 9月5日 08:00", text)
    }
}
