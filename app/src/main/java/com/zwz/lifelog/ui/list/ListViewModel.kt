package com.zwz.lifelog.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Templates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Filter { ALL, DUE, NONE, PINNED }

data class ListUiState(
    val all: List<EventStatus> = emptyList(),
    val keyword: String = "",
    val filter: Filter = Filter.ALL,
    val tagFilter: String? = null,
    val showTemplatePicker: Boolean = false,
    val pendingUndo: Pair<Long, Long>? = null   // (recordId, eventId)
) {
    val visible: List<EventStatus> get() = filtered(all, keyword, filter, tagFilter)

    val allTags: List<String> get() = all.mapNotNull { it.event.tag }.distinct().sorted()

    companion object {
        fun filtered(
            all: List<EventStatus>,
            keyword: String,
            filter: Filter,
            tag: String?
        ): List<EventStatus> {
            var list = all
            val kw = keyword.trim()
            if (kw.isNotBlank()) {
                list = list.filter { s ->
                    s.event.name.contains(kw, ignoreCase = true) ||
                            s.records.any { (it.note ?: "").contains(kw, ignoreCase = true) } ||
                            (s.event.tag ?: "").contains(kw, ignoreCase = true)
                }
            }
            if (tag != null) list = list.filter { it.event.tag == tag }
            list = when (filter) {
                Filter.ALL -> list
                Filter.DUE -> list.filter { it.freshness == Freshness.DUE }
                Filter.NONE -> list.filter { it.records.isEmpty() }
                Filter.PINNED -> list.filter { it.event.isPinned }
            }
            // 钉选优先 → 状态最紧急优先 → 名称
            return list.sortedWith(
                compareByDescending<EventStatus> { it.event.isPinned }
                    .thenByDescending { it.ratio }
                    .thenBy { it.event.name }
            )
        }
    }
}

class ListViewModel(private val repo: LifeLogRepository) : ViewModel() {

    private val keyword = MutableStateFlow("")
    private val filter = MutableStateFlow(Filter.ALL)
    private val tagFilter = MutableStateFlow<String?>(null)
    private val showTemplate = MutableStateFlow(false)
    private val pendingUndo = MutableStateFlow<Pair<Long, Long>?>(null)

    val ui: StateFlow<ListUiState> = combine(
        repo.statuses(), keyword, filter, tagFilter, showTemplate, pendingUndo
    ) { arr ->
        val statuses: List<EventStatus> = arr[0] as List<EventStatus>
        @Suppress("UNCHECKED_CAST")
        ListUiState(
            all = statuses,
            keyword = arr[1] as String,
            filter = arr[2] as Filter,
            tagFilter = arr[3] as String?,
            showTemplatePicker = arr[4] as Boolean,
            pendingUndo = arr[5] as Pair<Long, Long>?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListUiState())

    fun onKeyword(v: String) { keyword.value = v }
    fun onFilter(f: Filter) { filter.value = f }
    fun onTag(t: String?) { tagFilter.value = t }

    fun quickRecord(eventId: Long, onDone: (String) -> Unit) = viewModelScope.launch {
        repo.quickRecord(eventId)
        val name = repo.eventById(eventId)?.name ?: ""
        onDone(name)
    }

    fun showTemplatePicker() { showTemplate.value = true }
    fun hideTemplatePicker() { showTemplate.value = false }

    fun importTemplates(names: Set<String>, onDone: (Int) -> Unit) = viewModelScope.launch {
        val n = repo.importTemplates(names)
        showTemplate.value = false
        onDone(n)
    }

    val templateNames: List<String> = Templates.ALL.map { it.name }
}
