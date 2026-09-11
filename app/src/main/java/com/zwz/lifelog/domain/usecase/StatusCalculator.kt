package com.zwz.lifelog.domain.usecase

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.util.DayDiff
import kotlin.math.roundToInt

/**
 * 事件状态计算 —— 整个应用的核心逻辑。
 *
 * 三条判定路径，按 [Event.kind] 与 [Event.timesPerDay] 分派：
 *
 * | 情形 | 判定依据 | 典型事件 |
 * |---|---|---|
 * | [EventKind.ON_DEMAND] | 恒定 `IDLE`，不回答「该不该做」 | 感冒看病、针灸 |
 * | [Event.timesPerDay] 非空 | **当日**完成次数 | 一天三次的药 |
 * | 其余（PERIODIC） | 距上次记录 ÷ 基准间隔 | 理发、换床单 |
 * | [EventKind.COURSE] | 由子事件聚合（见 [aggregateCourse]） | 感冒 2026-09 |
 *
 * ⚠️ 两个「天」的口径是分开的，不要合并：
 * - `daysAgo` 走 [DayDiff.calendarDays]，按自然日，只喂给显示层；
 * - `ratio` 走 [DayDiff.elapsedDays]，按真实流逝时长（含小数），只喂给状态判断。
 * 合并回一个值就会重现「9/2 22:00 的事在 9/4 显示成昨天」。
 *
 * ⚠️ 频次型事件的 `ratio` 是**当日进度**（doneToday / timesPerDay），
 * 与周期型的「已流逝 ÷ 基准」不是一个东西，别混着用。
 */
object StatusCalculator {

    private const val DAY_MILLIS = 86_400_000L
    private const val DEFAULT_BASELINE_DAYS = 30

    /** 相邻两次记录的平均间隔（毫秒）。记录不足两条时返回 null。 */
    fun averageGapMillis(recordsAsc: List<Record>): Long? {
        if (recordsAsc.size < 2) return null
        val span: Long = recordsAsc.last().timestamp - recordsAsc.first().timestamp
        if (span <= 0L) return null
        return span / (recordsAsc.size - 1).toLong()
    }

    // ------------------------------------------------------------------
    // 判定核心
    // ------------------------------------------------------------------

    /**
     * 一次判定的结果。
     *
     * @param baselineDays 判断基准（天数）；频次型事件固定为 1，表示「按天算」
     * @param ratio        进度比例；周期型是「已流逝 ÷ 基准」，频次型是「今日已完成 ÷ 目标」
     */
    private data class Judged(
        val freshness: Freshness,
        val ratio: Float,
        val baselineDays: Float,
        val predictedNextMillis: Long?
    )

    /**
     * 分派判定。
     *
     * @param doneToday 当日已完成次数（由 SQL 聚合给出，不读记录表）
     */
    private fun judge(
        event: Event,
        lastAsc: Long?,
        doneToday: Int,
        baselineDays: Float,
        now: Long
    ): Judged {
        if (lastAsc == null) {
            return Judged(Freshness.NONE, 0f, baselineDays, null)
        }

        // 疗程自己不承载记录，状态一律由子事件聚合覆盖；
        // 万一有人给它记了一笔，也不该凭空变成「该做了」。
        if (event.kind == EventKind.COURSE) {
            return Judged(Freshness.NONE, 0f, baselineDays, null)
        }

        // 按需：只回答「上次距今多久」，不催
        if (event.kind == EventKind.ON_DEMAND) {
            return Judged(Freshness.IDLE, 0f, baselineDays, null)
        }

        // 频次型：只看今天完成了几次，与「距上次多久」无关
        val perDay = event.timesPerDay
        if (perDay != null && perDay > 0) {
            val done = doneToday.coerceAtLeast(0)
            val ratio = done.toFloat() / perDay.toFloat()
            val freshness = when {
                done >= perDay -> Freshness.FRESH
                done == 0 -> Freshness.DUE
                else -> Freshness.SOON
            }
            // 基准写 1：频次型的节奏单位是「天」，间隔型基准对它没有意义
            return Judged(freshness, ratio, 1f, null)
        }

        // 周期型：现有逻辑
        val safeBaseline: Float = if (baselineDays < 1f) 1f else baselineDays
        val ratio: Float = DayDiff.elapsedDays(lastAsc, now) / safeBaseline
        val freshness: Freshness = when {
            ratio >= 1f -> Freshness.DUE
            ratio >= 0.75f -> Freshness.SOON
            else -> Freshness.FRESH
        }
        return Judged(
            freshness = freshness,
            ratio = ratio,
            baselineDays = safeBaseline,
            predictedNextMillis = lastAsc + (safeBaseline * DAY_MILLIS.toFloat()).toLong()
        )
    }

    /** 基准天数：优先用户设定 → 历史平均 → 30 天兜底。只对周期型有意义。 */
    private fun baselineOf(event: Event, avg: Long?): Float {
        if (event.targetDays != null && event.targetDays > 0) return event.targetDays.toFloat()
        if (avg != null && avg > 0L) return avg.toFloat() / DAY_MILLIS.toFloat()
        return DEFAULT_BASELINE_DAYS.toFloat()
    }

    private fun avgOf(count: Int, firstAsc: Long?, lastAsc: Long?): Long? =
        if (count >= 2 && firstAsc != null && lastAsc != null) {
            val span = lastAsc - firstAsc
            if (span > 0L) span / (count - 1).toLong() else null
        } else null

    // ------------------------------------------------------------------
    // 首页列表：不加载记录
    // ------------------------------------------------------------------

    /**
     * 基于聚合统计计算状态，不加载记录列表。
     *
     * 用于首页列表：数据库层用 `COUNT / MIN / MAX` 直接算出这三个值，
     * 避免把该事件的上千条历史记录读进内存再遍历。
     *
     * @param count     记录条数
     * @param firstAsc  最早一次记录的时间戳（无记录时为 null）
     * @param lastAsc   最近一次记录的时间戳（无记录时为 null）
     * @param doneToday 当日已完成次数（频次型事件用）
     */
    fun computeLite(
        event: Event,
        count: Int,
        firstAsc: Long?,
        lastAsc: Long?,
        now: Long = System.currentTimeMillis(),
        doneToday: Int = 0
    ): EventStatusLite {
        val avg: Long? = avgOf(count, firstAsc, lastAsc)
        val baselineDays: Float = baselineOf(event, avg)
        val j: Judged = judge(event, lastAsc, doneToday, baselineDays, now)

        val daysAgo: Int? = lastAsc?.let { DayDiff.calendarDays(it, now) }

        return EventStatusLite(
            event = event,
            recordCount = count,
            lastTimestamp = lastAsc,
            firstTimestamp = firstAsc,
            daysAgo = daysAgo,
            avgGapMillis = avg,
            baselineDays = j.baselineDays.roundToInt(),
            freshness = j.freshness,
            ratio = j.ratio,
            predictedNextMillis = j.predictedNextMillis,
            doneToday = doneToday
        )
    }

    // ------------------------------------------------------------------
    // 详情页
    // ------------------------------------------------------------------

    /**
     * 分页版状态计算。
     *
     * 与 [compute] 的区别：
     * - `recordsDesc` 只是**最近若干条**（倒序），不含全部历史
     * - 统计信息由 [count] / [firstAsc] / [lastAsc] 提供，
     *   这三个值走 SQL 聚合，基于**全量**记录
     *
     * 因此即使这个事件有 5000 条记录，详情页也只加载 20 条，
     * 但显示的「累计次数」仍是 5000。
     */
    fun computePaged(
        event: Event,
        recordsDesc: List<Record>,
        count: Int,
        firstAsc: Long?,
        lastAsc: Long?,
        now: Long = System.currentTimeMillis(),
        doneToday: Int = 0,
        children: List<EventStatusLite> = emptyList()
    ): EventStatus {
        val avg: Long? = avgOf(count, firstAsc, lastAsc)
        val baselineDays: Float = baselineOf(event, avg)
        val j: Judged = judge(event, lastAsc, doneToday, baselineDays, now)

        val st = EventStatus(
            event = event,
            records = recordsDesc,
            recordCount = count,
            lastTimestamp = lastAsc,
            firstTimestamp = firstAsc,
            daysAgo = lastAsc?.let { DayDiff.calendarDays(it, now) },
            avgGapMillis = avg,
            baselineDays = j.baselineDays.roundToInt(),
            freshness = j.freshness,
            ratio = j.ratio,
            predictedNextMillis = j.predictedNextMillis,
            doneToday = doneToday,
            children = children
        )
        // 疗程自身不承载记录，状态直接换成子事件聚合结果
        return if (event.kind == EventKind.COURSE) aggregateCourse(st) else st
    }

    fun compute(
        event: Event,
        recordsAsc: List<Record>,
        now: Long = System.currentTimeMillis(),
        doneToday: Int = 0
    ): EventStatus {
        val last: Long? = recordsAsc.lastOrNull()?.timestamp
        val avg: Long? = averageGapMillis(recordsAsc)
        val j: Judged = judge(event, last, doneToday, baselineOf(event, avg), now)

        return EventStatus(
            event = event,
            records = recordsAsc,
            recordCount = recordsAsc.size,
            lastTimestamp = last,
            firstTimestamp = recordsAsc.firstOrNull()?.timestamp,
            daysAgo = last?.let { DayDiff.calendarDays(it, now) },
            avgGapMillis = avg,
            baselineDays = j.baselineDays.roundToInt(),
            freshness = j.freshness,
            ratio = j.ratio,
            predictedNextMillis = j.predictedNextMillis,
            doneToday = doneToday
        )
    }

    // ------------------------------------------------------------------
    // 疗程聚合
    // ------------------------------------------------------------------

    /** 状态紧急度排序：越大越该看。 */
    private fun priority(f: Freshness): Int = when (f) {
        Freshness.DUE -> 5
        Freshness.SOON -> 4
        Freshness.FRESH -> 3
        Freshness.NONE -> 2
        Freshness.IDLE -> 1
    }

    /**
     * 用子事件重算疗程的状态。
     *
     * 疗程本身不承载记录，它的意义是「这一摊事现在怎么样了」：
     * 取子事件里最紧急的一档，只要有一个子项该做了，用户就得看一眼。
     *
     * 今日进度是各子事件之和（「今日 2/5」= 五种药里吃完了两顿的量）。
     */
    fun aggregateCourse(parent: EventStatus): EventStatus {
        val kids = parent.children
        if (kids.isEmpty()) return parent.copy(freshness = Freshness.NONE, ratio = 0f)

        val target = kids.sumOf { it.event.timesPerDay?.coerceAtLeast(0) ?: 0 }
        val done = kids.sumOf { it.doneToday }
        val urgent = kids.maxByOrNull { priority(it.freshness) }

        val ratio: Float = if (target > 0) done.toFloat() / target.toFloat() else 0f

        return parent.copy(
            recordCount = kids.sumOf { it.recordCount },
            lastTimestamp = kids.mapNotNull { it.lastTimestamp }.maxOrNull(),
            daysAgo = kids.mapNotNull { it.daysAgo }.minOrNull(),
            freshness = urgent?.freshness ?: Freshness.NONE,
            ratio = ratio,
            predictedNextMillis = null,
            doneToday = done
        )
    }

    /**
     * [aggregateCourse] 的列表版（首页用）。
     *
     * @param children 只包含**未归档**子事件
     */
    fun aggregateCourseLite(
        parent: EventStatusLite,
        children: List<EventStatusLite>
    ): EventStatusLite {
        if (children.isEmpty()) return parent.copy(freshness = Freshness.NONE, ratio = 0f)

        val target = children.sumOf { it.event.timesPerDay?.coerceAtLeast(0) ?: 0 }
        val done = children.sumOf { it.doneToday }
        val urgent = children.maxByOrNull { priority(it.freshness) }
        val ratio: Float = if (target > 0) done.toFloat() / target.toFloat() else 0f

        return parent.copy(
            recordCount = children.sumOf { it.recordCount },
            lastTimestamp = children.mapNotNull { it.lastTimestamp }.maxOrNull(),
            daysAgo = children.mapNotNull { it.daysAgo }.minOrNull(),
            avgGapMillis = null,
            freshness = urgent?.freshness ?: Freshness.NONE,
            ratio = ratio,
            predictedNextMillis = null,
            doneToday = done,
            children = children
        )
    }

    /** 疗程的今日进度文案，如「今日 2/5」；无频次子事件时返回 null。 */
    fun courseProgress(parent: EventStatusLite): String? {
        val target = parent.children.sumOf { it.event.timesPerDay?.coerceAtLeast(0) ?: 0 }
        if (target <= 0) return null
        return "今日 ${parent.doneToday}/$target"
    }
}
