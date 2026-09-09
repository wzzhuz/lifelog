package com.zwz.lifelog.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.HomeLayoutMode
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.domain.model.Templates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

/** 「距上次多久」这类文案的刷新节奏。1 分钟足够，再密只会白白唤醒 UI。 */
private const val TICK_MS = 60_000L

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

    /** 当前布局模式。由 UI 注入，决定用哪个排序字段的查询。 */
    private val layoutFlow = MutableStateFlow(HomeLayoutMode.COMPACT)

    fun onLayoutMode(mode: HomeLayoutMode) { layoutFlow.value = mode }

    /**
     * 事件列表。
     *
     * 两种模式走**不同的查询**：
     * - 列表模式：ORDER BY sortOrder
     * - 分组模式：ORDER BY tag, sortInGroup
     *
     * 因为两个字段各管各的排序，不能共用一个流。
     */
    private val statuses: StateFlow<List<EventStatusLite>> = layoutFlow
        .flatMapLatest { mode ->
            if (mode == HomeLayoutMode.GROUPED) repo.statusesLiteGrouped()
            else repo.statusesLite()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 一分钟走一次的「现在」，供界面渲染「8 小时前」这类文案。
     *
     * 为什么需要它：列表数据只在**数据库变化**时才发射，
     * 而「距上次多久」是渲染时算的。App 停在首页不动，
     * 文案就会一直停在打开那一刻——记完一笔看着还是「3 小时前」，
     * 过了一小时也没变化。
     *
     * 刻意**不**把 tick 接进 [statuses]：那会让 Room 的 Flow
     * 每分钟被取消重启一次，白白多出几十次聚合查询。
     * 这里只给 UI 提供一个时刻，数据查询照旧按需触发。
     */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    val now: StateFlow<Long> = ticker.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis()
    )

    val ui: StateFlow<ListUiState> = combine(
        statuses, query, showTemplate, pendingUndo
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
     * @param visibleIds 当前**可见**列表拖拽后的顺序
     *
     * 只传可见项，这里补齐被筛掉的项再一起编号。
     * 若只给可见项编号 0..n-1，被筛掉的项会保留旧值与之冲突，
     * 一旦切回「不限」就会出现顺序错乱。
     */
    /**
     * 保存拖拽后的顺序（挂起版）。
     *
     * 做成挂起而不是 fire-and-forget，是为了让调用方能准确知道
     * 「写库完成」这个时刻。UI 需要在写库期间暂停用数据库回传的
     * 顺序覆盖本地列表，否则刚拖好的顺序会被中间态冲掉——
     * 靠 launch 前后打标记是拦不住的，launch 会立刻返回。
     */
    suspend fun saveOrder(visibleIds: List<Long>) {
        val all = ui.value.all.map { it.event.id }
        val hidden = all.filter { it !in visibleIds }
        repo.saveSortOrder(visibleIds + hidden)
    }

    /**
     * 分组模式保存组内顺序。
     *
     * **只改 sortInGroup，不碰 sortOrder**。
     * 这正是本次修复的核心：此前共用一个字段，
     * 在分组里拖一下会把全局顺序重写成按标签排列。
     */
    /** [saveOrder] 的分组版，同样是挂起版。 */
    suspend fun saveGroupOrder(groupIds: List<Long>) {
        repo.saveGroupOrder(groupIds)
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
