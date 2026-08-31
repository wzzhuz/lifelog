package com.zwz.lifelog.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.Record
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
        val kw = keyword.trim()
        val src = if (kw.isBlank()) rows else rows.filter {
            it.eventName.contains(kw, ignoreCase = true) ||
                    (it.record.note ?: "").contains(kw, ignoreCase = true)
        }
        return src
            .groupBy { com.zwz.lifelog.util.TimeFormatter.monthKey(it.record.timestamp) }
            .toList()
            .sortedByDescending { pair -> pair.value.maxOf { it.record.timestamp } }
            .map { (month, list) -> month to list.sortedByDescending { it.record.timestamp } }
    }
}

class TimelineViewModel(private val repo: LifeLogRepository) : ViewModel() {

    val ui: StateFlow<TimelineUi> = repo.snapshotFlow().map { snap ->
        val nameOf = snap.events.associateBy { it.id }
        TimelineUi(
            rows = snap.records
                .sortedByDescending { it.timestamp }
                .mapNotNull { r ->
                    val ev = nameOf[r.eventId] ?: return@mapNotNull null
                    TimelineRow(r, ev.id, ev.name, ev.emoji)
                }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUi())
}
