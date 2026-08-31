package com.zwz.lifelog.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.util.TimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TimelineRow(
    val record: Record,
    val eventId: Long,
    val eventName: String,
    val emoji: String
)

data class TimelineUi(
    val rows: List<TimelineRow> = emptyList(),
    val keyword: String = ""
) {
    /** 按月份分组，月份内按时间倒序。 */
    fun grouped(): List<Pair<String, List<TimelineRow>>> {
        val kw: String = keyword.trim()
        val src: List<TimelineRow> = if (kw.isBlank()) {
            rows
        } else {
            rows.filter { row ->
                row.eventName.contains(kw, ignoreCase = true) ||
                        (row.record.note ?: "").contains(kw, ignoreCase = true)
            }
        }
        // 注意：Pair 只有 first / second，没有 key / value
        // （key/value 是 Map.Entry 的属性，用错会报 Unresolved reference）
        return src
            .groupBy { row -> TimeFormatter.monthKey(row.record.timestamp) }
            .toList()
            .sortedByDescending { pair -> pair.second.maxOf { row -> row.record.timestamp } }
            .map { entry ->
                val month: String = entry.first
                val list: List<TimelineRow> =
                    entry.second.sortedByDescending { row -> row.record.timestamp }
                month to list
            }
    }
}

class TimelineViewModel(private val repo: LifeLogRepository) : ViewModel() {

    val ui: StateFlow<TimelineUi> = repo.snapshotFlow()
        .map { snap ->
            val nameOf = snap.events.associateBy { it.id }
            val rows: List<TimelineRow> = snap.records
                .sortedByDescending { it.timestamp }
                .mapNotNull { record ->
                    val ev = nameOf[record.eventId] ?: return@mapNotNull null
                    TimelineRow(
                        record = record,
                        eventId = ev.id,
                        eventName = ev.name,
                        emoji = ev.emoji
                    )
                }
            TimelineUi(rows = rows)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUi())
}
