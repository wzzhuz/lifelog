package com.zwz.lifelog

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.usecase.StatusCalculator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 首页「记完一笔却不刷新」的回归测试。
 *
 * 根因在 ListScreen：本地拖拽列表与数据库回传列表同步时，
 * 只比对了 event.id 的顺序。记完一笔后顺序往往没变
 * （事件被钉选、或列表里就这一个事件），于是被判成「无需同步」，
 * 卡片上仍是旧的「上次 3 天前」——非得进详情页再回来才刷新，
 * 因为那时 remember 重建、本地列表为空，才会被重新填充。
 *
 * 这里的断言锁定的就是那条判定：内容变了就必须同步，
 * 与顺序是否变化无关。
 */
class HomeListSyncTest {

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

    /**
     * 记一笔之后：顺序没变，但内容必须判定为「变了」。
     *
     * 只比 id 顺序的实现会漏掉这次更新，这正是那个 bug。
     */
    @Test
    fun `记一笔后顺序可能不变但内容一定变`() {
        val event = Event(id = 1, name = "吃药", targetDays = 1)
        val first = ts("2026-09-05T08:00")
        val now = ts("2026-09-05T16:00")

        // 记之前：只记过一次
        val before: List<EventStatusLite> = listOf(
            StatusCalculator.computeLite(event, count = 1, firstAsc = first, lastAsc = first, now = now)
        )
        // 刚刚又记了一笔
        val after: List<EventStatusLite> = listOf(
            StatusCalculator.computeLite(event, count = 2, firstAsc = first, lastAsc = now, now = now)
        )

        // 只有这一个事件，顺序当然没变 —— 老实现在这里就停了
        assertEquals(before.map { it.event.id }, after.map { it.event.id })
        // 内容变了：次数、平均间隔、距今天数都不同
        assertNotEquals(before, after)
        assertNotEquals(before[0].recordCount, after[0].recordCount)
    }

    /** 钉选在最前时，即便 ratio 变了排序也不变，同样只能靠内容判断。 */
    @Test
    fun `钉选事件的排序不随记录变化`() {
        val pinned = Event(id = 1, name = "吃药", targetDays = 1, isPinned = true)
        val other = Event(id = 2, name = "理发", targetDays = 35)
        val now = ts("2026-09-05T16:00")

        fun build(lastMed: Long): List<EventStatusLite> = listOf(
            StatusCalculator.computeLite(other, 1, ts("2026-08-20T10:00"), ts("2026-08-20T10:00"), now),
            StatusCalculator.computeLite(pinned, 2, ts("2026-09-05T06:00"), lastMed, now)
        ).sortedWith(
            compareByDescending<EventStatusLite> { it.event.isPinned }
                .thenBy { it.event.sortOrder }
                .thenByDescending { it.ratio }
                .thenBy { it.event.name }
        )

        val before = build(ts("2026-09-05T06:00"))
        val after = build(ts("2026-09-05T15:00"))

        // 吃药钉在最前，理发在后：无论记没记，顺序都是 [吃药, 理发]
        assertEquals(
            listOf("吃药", "理发"),
            before.map { it.event.name }
        )
        assertEquals(before.map { it.event.id }, after.map { it.event.id })
        // 但内容必须判定为变了
        assertNotEquals(before, after)
    }

    /** 反过来：确实没变化时不应该白白重建列表。 */
    @Test
    fun `内容没变就不需要同步`() {
        val event = Event(id = 1, name = "吃药", targetDays = 1)
        val first = ts("2026-09-05T08:00")
        val now = ts("2026-09-05T16:00")

        val a = StatusCalculator.computeLite(event, 1, first, first, now)
        val b = StatusCalculator.computeLite(event, 1, first, first, now)

        assertEquals(listOf(a), listOf(b))
    }
}
