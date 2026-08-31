package com.zwz.lifelog.domain.usecase

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Record
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 事件状态计算 —— 整个应用的核心逻辑。
 *
 * 设计要点：没有设置期望间隔的事件（例如「看病」这种没有规律的事），
 * 在记录满两次之后会自动使用历史平均间隔作为判断基准。
 * 这是相比「只显示距今天数」的关键增强：能直接回答「现在该不该做」。
 */
object StatusCalculator {

    private const val DAY_MILLIS = 86_400_000L
    private const val DEFAULT_BASELINE_DAYS = 30

    fun daysSince(timestamp: Long, now: Long = System.currentTimeMillis()): Int =
        ((now - timestamp) / DAY_MILLIS).toInt().coerceAtLeast(0)

    /** 相邻记录的间隔，按时间升序；只有一条记录时返回 null。 */
    fun averageGapMillis(recordsAsc: List<Record>): Long? {
        if (recordsAsc.size < 2) return null
        val span = recordsAsc.last().timestamp - recordsAsc.first().timestamp
        return (span / (recordsAsc.size - 1)).roundToLong()
    }

    fun compute(
        event: Event,
        recordsAsc: List<Record>,
        now: Long = System.currentTimeMillis()
    ): EventStatus {
        val last = recordsAsc.lastOrNull()?.timestamp
        val avg = averageGapMillis(recordsAsc)

        if (last == null) {
            return EventStatus(
                event = event,
                records = recordsAsc,
                lastTimestamp = null,
                daysSince = null,
                avgGapMillis = null,
                baselineDays = event.targetDays ?: DEFAULT_BASELINE_DAYS,
                freshness = Freshness.NONE,
                ratio = 0f,
                predictedNextMillis = null
            )
        }

        val d = daysSince(last, now)
        val baseline = event.targetDays ?: (avg?.let { (it / DAY_MILLIS).toFloat() }
            ?: DEFAULT_BASELINE_DAYS.toFloat())
        val safeBaseline = baseline.coerceAtLeast(1f)
        val ratio = d / safeBaseline

        val freshness = when {
            ratio >= 1f -> Freshness.DUE
            ratio >= 0.75f -> Freshness.SOON
            else -> Freshness.FRESH
        }

        val next = last + (safeBaseline * DAY_MILLIS).toLong()

        return EventStatus(
            event = event,
            records = recordsAsc,
            lastTimestamp = last,
            daysSince = d,
            avgGapMillis = avg,
            baselineDays = safeBaseline.roundToInt(),
            freshness = freshness,
            ratio = ratio,
            predictedNextMillis = next
        )
    }
}
