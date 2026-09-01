package com.zwz.lifelog.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Templates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Filter { ALL, DUE, NONE, PINNED }

/**
 * 查询条件聚合。
 *
 * 把关键词、筛选、标签合并成一个对象，是为了让 `combine` 的参数
 * 保持在它支持的个数内（Kotlin 的 combine 最多 5 个流）。
 */
private data class QueryState(
    val keyword: String = "",
    /** 备注全文搜索命中的事件 id；null 表示没有关键词，无需过滤 */
    val searchHits: Set<Long>? = null,
    val filter: Filter = Filter.ALL,
    val tag: String? = null
)

data class ListUiState(
    val all: List<EventStatusLite> = emptyList(),
    val keyword: String = "",
    /** 备注全文搜索命中的事件 id（异步到达） */
    val searchHits: Set<Long>? = null,
    val filter: Filter = Filter.ALL,
    val tagFilter: String? = null,
    /**
     * 全部事件的标签（不受当前筛选影响）。
     *
     * 曾经从「当前可见事件」推导，导致选中某个分类后
     * 分类行只剩那一个可点，用户无法切换到别的分类。
     */
    val allTags: List<String> = emptyList(),
    val showTemplatePicker: Boolean = false,
    val pendingUndo: Pair<Long, Long>? = null   // (recordId, eventId)
) {
    val visible: List<EventStatusLite> get() = filtered(all, keyword, searchHits, filter, tagFilter)

    companion object {
        fun filtered(
            all: List<EventStatusLite>,
            keyword: String,
            searchHits: Set<Long>?,
            filter: Filter,
            tag: String?
        ): List<EventStatusLite> {
            var list = all
            val kw = keyword.trim()
            if (kw.isNotBlank()) {
                // 事件名与标签在内存里直接匹配（只有几十个事件，立即响应）；
                // 备注全文由 SQL 查出命中的 id 集合，异步到达后自动补充。
                list = list.filter { s ->
                    s.event.name.contains(kw, ignoreCase = true) ||
                            (s.event.tag ?: "").contains(kw, ignoreCase = true) ||
                            (searchHits?.contains(s.event.id) ?: false)
                }
            }
            if (tag != null) list = list.filter { it.event.tag == tag }
            list = when (filter) {
                Filter.ALL -> list
                Filter.DUE -> list.filter { it.freshness == Freshness.DUE }
                Filter.NONE -> list.filter { it.recordCount == 0 }
                Filter.PINNED -> list.filter { it.event.isPinned }
            }
            // 钉选（置顶层） → 手动顺序 → 状态最紧急 → 名称
            //
            // sortOrder 字段数据库里一直存在、DAO 也按它排，
            // 但此前**没有任何界面能修改它**，排序实际是「钉选→紧急度→名称」，
            // 等于这个字段白留。现在拖拽会写入它，排序逻辑也随之启用。
            return list.sortedWith(
                compareByDescending<EventStatusLite> { it.event.isPinned }
                    .thenBy { it.event.sortOrder }
                    .thenByDescending { it.ratio }
                    .thenBy { it.event.name }
            )
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ListViewModel(private val repo: LifeLogRepository) : ViewModel() {

    private val keywordFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(Filter.ALL)
    private val tagFlow = MutableStateFlow<String?>(null)
    private val showTemplate = MutableStateFlow(false)
    private val pendingUndo = MutableStateFlow<Pair<Long, Long>?>(null)

    /**
     * 关键词 → 命中集合。
     *
     * 防抖 150ms：连续打字时不打断用户，停手后才查一次库。
     * 关键词为空直接返回 null，跳过查询。
     */
    private val searchHits: StateFlow<Set<Long>?> = keywordFlow
        .debounce(150)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest< String, Set<Long>? > { kw ->
            // 必须显式标注 Set<Long>?：
            // 写 flowOf(null) 会被推断成 Flow<Nothing?>，
            // 与 else 分支的 Flow<Set<Long>> 合不到一起，编译直接失败。
            if (kw.isBlank()) {
                flowOf<Set<Long>?>(null)
            } else {
                flow { emit(repo.searchMatchedEventIds(kw)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val query: StateFlow<QueryState> = combine(
        keywordFlow, searchHits, filterFlow, tagFlow
    ) { kw, hits, f, t -> QueryState(kw, hits, f, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueryState())

    val ui: StateFlow<ListUiState> = combine(
        repo.statusesLite(), query, showTemplate, pendingUndo
    ) { statuses, q, showTpl, undo ->
        ListUiState(
            all = statuses,
            keyword = q.keyword,
            searchHits = q.searchHits,
            filter = q.filter,
            tagFilter = q.tag,
            allTags = statuses.mapNotNull { it.event.tag }.distinct().sorted(),
            showTemplatePicker = showTpl,
            pendingUndo = undo
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListUiState())

    fun onKeyword(v: String) { keywordFlow.value = v }
    fun onFilter(f: Filter) { filterFlow.value = f }
    fun onTag(t: String?) { tagFlow.value = t }

    fun quickRecord(eventId: Long, onDone: (String) -> Unit) = viewModelScope.launch {
        repo.quickRecord(eventId)
        val name = repo.eventById(eventId)?.name ?: ""
        onDone(name)
    }

    /**
     * 保存拖拽后的顺序。
     *
     * @param orderedIds 当前可见列表拖拽后的**完整**顺序
     */
    fun saveOrder(orderedIds: List<Long>) = viewModelScope.launch {
        repo.saveSortOrder(orderedIds)
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
