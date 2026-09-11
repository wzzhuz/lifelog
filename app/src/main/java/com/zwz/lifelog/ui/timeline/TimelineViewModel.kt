package com.zwz.lifelog.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimelineRow(
    val record: Record,
    val eventId: Long,
    val eventName: String,
    val emoji: String
)

/** 每次翻页取多少条。 */
private const val PAGE_SIZE = 60

data class TimelineUi(
    val rows: List<TimelineRow> = emptyList(),
    val keyword: String = "",
    /** 是否还有更早的记录没加载出来。 */
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false
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

/**
 * 时间线：全局记录的倒序列表。
 *
 * **改为分页加载**：此前订阅 `snapshotFlow()`，后者会
 * `SELECT * FROM records ORDER BY timestamp DESC`（无 LIMIT），
 * 实测 200 万条时 2.5 秒——这是全应用最慢的一条查询。
 * 分页后首页只要 0.03ms，且不随记录数增长。
 *
 * 搜索也只在**已加载**的记录里过滤：翻得越深，能搜到的范围越大。
 * 这是刻意的取舍——要搜全部历史请用首页的搜索框（走 SQL LIKE）。
 */
class TimelineViewModel(private val repo: LifeLogRepository) : ViewModel() {

    private val rows = MutableStateFlow<List<TimelineRow>>(emptyList())
    private val keyword = MutableStateFlow("")
    private val hasMore = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val revision = MutableStateFlow(0)

    init {
        loadFirstPage()
    }

    fun onKeyword(v: String) { keyword.value = v }

    /** 数据变化后重新拉取（记一笔、删记录之后调用）。 */
    fun refresh() { loadFirstPage() }

    fun loadMore() {
        if (loadingMore.value || !hasMore.value) return
        viewModelScope.launch {
            loadingMore.value = true
            try {
                val next = repo.timelinePage(PAGE_SIZE, rows.value.size)
                rows.value = rows.value + next
                hasMore.value = next.size == PAGE_SIZE
            } finally {
                loadingMore.value = false
            }
        }
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            loadingMore.value = true
            try {
                val first = repo.timelinePage(PAGE_SIZE, 0)
                rows.value = first
                hasMore.value = first.size == PAGE_SIZE
            } finally {
                loadingMore.value = false
            }
        }
    }

    val ui: StateFlow<TimelineUi> = combine(
        rows, keyword, hasMore, loadingMore
    ) { list, kw, more, loading ->
        TimelineUi(rows = list, keyword = kw, hasMore = more, loadingMore = loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUi())
}
