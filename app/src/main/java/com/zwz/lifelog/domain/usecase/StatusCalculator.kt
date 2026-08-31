package com.zwz.lifelog.domain.usecase

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Record
import kotlin.math.roundToInt

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

    fun daysSince(timestamp: Long, now: Long = System.currentTimeMillis()): Int {
        val diff = now - timestamp
        if (diff <= 0L) return 0
        return (diff / DAY_MILLIS).toInt()
    }

    /** 相邻两次记录的平均间隔（毫秒）。记录不足两条时返回 null。 */
    fun averageGapMillis(recordsAsc: List<Record>): Long? {
        if (recordsAsc.size < 2) return null
        val span: Long = recordsAsc.last().timestamp - recordsAsc.first().timestamp
        if (span <= 0L) return null
        return span / (recordsAsc.size - 1).toLong()
    }

    fun compute(
        event: Event,
        recordsAsc: List<Record>,
        now: Long = System.currentTimeMillis()
    ): EventStatus {
        val last: Long? = recordsAsc.lastOrNull()?.timestamp
        val avg: Long? = averageGapMillis(recordsAsc)

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

        val d: Int = daysSince(last, now)

        // 基准天数：优先用用户设定；其次用历史平均；都没有则兜底 30 天
        val baselineDays: Float = when {
            event.targetDays != null && event.targetDays > 0 -> event.targetDays.toFloat()
            avg != null && avg > 0L -> (avg.toFloat() / DAY_MILLIS.toFloat())
            else -> DEFAULT_BASELINE_DAYS.toFloat()
        }
        val safeBaseline: Float = if (baselineDays < 1f) 1f else baselineDays

        val ratio: Float = d.toFloat() / safeBaseline

        val freshness: Freshness = if (ratio >= 1f) {
            Freshness.DUE
        } else if (ratio >= 0.75f) {
            Freshness.SOON
        } else {
            Freshness.FRESH
        }

        val predicted: Long = last + (safeBaseline * DAY_MILLIS.toFloat()).toLong()

        return EventStatus(
            event = event,
            records = recordsAsc,
            lastTimestamp = last,
            daysSince = d,
            avgGapMillis = avg,
            baselineDays = safeBaseline.roundToInt(),
            freshness = freshness,
            ratio = ratio,
            predictedNextMillis = predicted
        )
    }
}
