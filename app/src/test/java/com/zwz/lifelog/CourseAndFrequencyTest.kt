package com.zwz.lifelog

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Templates
import com.zwz.lifelog.domain.usecase.StatusCalculator
import com.zwz.lifelog.ui.list.Filter
import com.zwz.lifelog.ui.list.ListUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 子事件 / 疗程 / 按需这三条新判定路径的回归测试。
 *
 * 起因是三个真实场景：
 * 1. 同时吃的几种药节奏不同，压成一个事件没法区分
 * 2. 感冒好了但「看病」还挂在首页，且被历史均值判成逾期
 * 3. 疗程结束后要一个个手工归档
 *
 * 这里锁定的都是「容易被后续改动顺手改坏」的判定：
 * 按需不催、频次只看当天、疗程取子事件最紧急的一档。
 */
class CourseAndFrequencyTest {

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

    private fun ts(text: String): Long =
        LocalDateTime.parse(text).atZone(shanghai).toInstant().toEpochMilli()

    // ---------- 按需：感冒不该被催 ----------

    @Test
    fun `感冒看病设为按需后永远不会变红`() {
        val first = ts("2026-03-01T10:00")
        val last = ts("2026-09-01T10:00")
        // 距今 182 天，历史平均恰好也是半年 —— 周期型会判成逾期
        val now = last + 182 * 86_400_000L

        val onDemand = StatusCalculator.computeLite(
            event = Event(id = 1, name = "看病", kind = EventKind.ON_DEMAND),
            count = 3, firstAsc = first, lastAsc = last, now = now
        )
        assertEquals(Freshness.IDLE, onDemand.freshness)

        // 同样的数据，周期型确实会变红 —— 两条路径必须分开
        val periodic = StatusCalculator.computeLite(
            event = Event(id = 2, name = "看病"),
            count = 3, firstAsc = first, lastAsc = last, now = now
        )
        assertEquals(Freshness.DUE, periodic.freshness)
    }

    @Test
    fun `按需事件没记过时是待记录而不是按需`() {
        val st = StatusCalculator.computeLite(
            event = Event(id = 1, name = "针灸", kind = EventKind.ON_DEMAND),
            count = 0, firstAsc = null, lastAsc = null
        )
        assertEquals(Freshness.NONE, st.freshness)
    }

    // ---------- 频次：一天三次只看今天 ----------

    @Test
    fun `一天三次的药按当天完成次数判状态`() {
        val event = Event(id = 1, name = "消炎药", timesPerDay = 3, parentId = 9)
        val last = ts("2026-09-05T08:00")
        val now = ts("2026-09-05T16:00")

        fun status(done: Int) = StatusCalculator.computeLite(
            event = event, count = 10, firstAsc = ts("2026-09-01T08:00"),
            lastAsc = last, now = now, doneToday = done
        )

        assertEquals(Freshness.DUE, status(0).freshness)
        assertEquals(Freshness.SOON, status(1).freshness)
        assertEquals(Freshness.SOON, status(2).freshness)
        assertEquals(Freshness.FRESH, status(3).freshness)
        // 多记一笔也不会被判成「超额」之外的新状态
        assertEquals(Freshness.FRESH, status(4).freshness)
    }

    @Test
    fun `刚吃过一次不代表今天齐了`() {
        val event = Event(id = 1, name = "消炎药", timesPerDay = 3)
        // 一小时前刚吃过，按「距上次多久」是新鲜，但今天才吃了 1/3
        val now = ts("2026-09-05T16:00")
        val st = StatusCalculator.computeLite(
            event = event, count = 5, firstAsc = ts("2026-09-04T08:00"),
            lastAsc = now - 3_600_000L, now = now, doneToday = 1
        )
        assertEquals(Freshness.SOON, st.freshness)
        assertEquals("今日 1/3", st.todayProgress)
    }

    // ---------- 疗程：取子事件最紧急的一档 ----------

    private fun lite(
        id: Long,
        name: String,
        timesPerDay: Int?,
        done: Int,
        ratio: Float = 0f,
        freshness: Freshness = Freshness.NONE,
        parentId: Long? = null,
        kind: EventKind = EventKind.PERIODIC
    ): EventStatusLite = EventStatusLite(
        event = Event(
            id = id, name = name, timesPerDay = timesPerDay,
            parentId = parentId, kind = kind
        ),
        recordCount = 0,
        lastTimestamp = null,
        daysAgo = null,
        avgGapMillis = null,
        baselineDays = 1,
        freshness = freshness,
        ratio = ratio,
        predictedNextMillis = null,
        doneToday = done
    )

    private fun course(id: Long = 1, name: String = "感冒 2026-09"): EventStatusLite =
        lite(id, name, null, 0, kind = EventKind.COURSE)

    @Test
    fun `疗程状态取子事件里最紧急的一档`() {
        val parent = course()
        val antipyretic = lite(2, "退烧药", 2, done = 2, freshness = Freshness.FRESH, parentId = 1)
        val antibiotic = lite(3, "消炎药", 3, done = 0, freshness = Freshness.DUE, parentId = 1)

        val agg = StatusCalculator.aggregateCourseLite(parent, listOf(antipyretic, antibiotic))

        // 有一个子项还没吃，整个疗程就该看一眼
        assertEquals(Freshness.DUE, agg.freshness)
        // 今日进度是各子项之和：2/5
        assertEquals(2, agg.doneToday)
        assertEquals(0.4f, agg.ratio, 0.0001f)
        assertEquals("今日 2/5", StatusCalculator.courseProgress(agg))
    }

    @Test
    fun `全部吃完时疗程才是新鲜的`() {
        val kids = listOf(
            lite(2, "退烧药", 2, done = 2, freshness = Freshness.FRESH, parentId = 1),
            lite(3, "消炎药", 3, done = 3, freshness = Freshness.FRESH, parentId = 1)
        )
        val agg = StatusCalculator.aggregateCourseLite(course(), kids)
        assertEquals(Freshness.FRESH, agg.freshness)
        assertEquals("今日 5/5", StatusCalculator.courseProgress(agg))
    }

    @Test
    fun `疗程没有子事件时是待记录`() {
        assertEquals(
            Freshness.NONE,
            StatusCalculator.aggregateCourseLite(course(), emptyList()).freshness
        )
    }

    // ---------- 首页：子事件紧跟自己的疗程 ----------

    @Test
    fun `子事件在首页紧跟着自己的疗程`() {
        fun st(id: Long, name: String, ratio: Float, parentId: Long? = null) =
            EventStatusLite(
                event = Event(id = id, name = name, parentId = parentId),
                recordCount = 1, lastTimestamp = null, daysAgo = null, avgGapMillis = null,
                baselineDays = 1, freshness = Freshness.SOON, ratio = ratio,
                predictedNextMillis = null
            )

        // 按紧急度排本来是：疗程 → 理发 → 退烧药（退烧药最不紧急）
        val ordered = ListUiState.filtered(
            all = listOf(st(1, "感冒 2026-09", 0.9f), st(2, "理发", 0.8f), st(3, "退烧药", 0.5f, parentId = 1)),
            keyword = "", searchHits = null, filter = Filter.ALL, tag = null
        )

        // 但退烧药属于感冒疗程，必须紧跟着它，否则看不出是同一次开的
        assertEquals(listOf("感冒 2026-09", "退烧药", "理发"), ordered.map { it.event.name })
    }

    // ---------- 按需事件的详情页指标 ----------

    @Test
    fun `按需事件有 isOnDemand 标记而周期型没有`() {
        val e = Event(id = 1, name = "看病", kind = EventKind.ON_DEMAND)
        assertTrue(e.isOnDemand)
        assertFalse(e.isCourse)
        // 周期型不误判成按需
        assertFalse(Event(id = 2, name = "理发", targetDays = 35).isOnDemand)
    }

    @Test
    fun `按需事件不产出预测值`() {
        val st = StatusCalculator.computeLite(
            event = Event(id = 1, name = "看病", kind = EventKind.ON_DEMAND),
            count = 3,
            firstAsc = ts("2026-03-01T10:00"),
            lastAsc = ts("2026-09-01T10:00"),
            now = ts("2026-09-01T10:00") + 182 * 86_400_000L
        )
        // 详情页靠「有没有预测值」决定是否显示「预计下次」
        assertNull(st.predictedNextMillis)
    }

    // ---------- 模板：子事件允许同名 ----------

    @Test
    fun `用药预设允许重复而长期事件不允许`() {
        // 长期事件里不应再有「吃药」：它总是属于某次疗程
        assertTrue(Templates.ALL.none { it.name == "吃药" })
        val presets = Templates.CHILD_PRESETS
        assertTrue(presets.isNotEmpty())
        presets.forEach { assertNotNull("预设 ${it.name} 缺每日次数", it.targetDays) }
    }

    // ---------- 首页：子事件与父事件层级 ----------

    @Test
    fun `子事件有父 id 而疗程没有`() {
        val child = Event(id = 2, name = "退烧药", parentId = 1)
        val parent = Event(id = 1, name = "感冒 2026-09", kind = EventKind.COURSE)
        assertTrue(child.isChild)
        assertFalse(parent.isChild)
        assertTrue(parent.isCourse)
    }
}
