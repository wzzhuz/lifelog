package com.zwz.lifelog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Record
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 详情页。
 *
 * 时间线**分页加载**，避免某个事件记录很多时一次性全读进内存。
 * 统计信息（次数 / 平均间隔 / 预测下次）仍基于全量数据，
 * 走 SQL 聚合而非遍历内存。
 */
class DetailViewModel(
    private val repo: LifeLogRepository,
    private val eventId: Long
) : ViewModel() {

    companion object {
        /** 首屏加载条数。 */
        const val PAGE_SIZE = 20
    }

    /** 已加载的记录（倒序）。首屏由 Flow 提供，更多靠 [loadMore] 追加。 */
    private val extra = MutableStateFlow<List<Record>>(emptyList())
    private val loading = MutableStateFlow(false)

    private val base = repo.statusOfPaged(eventId, PAGE_SIZE)

    val status: StateFlow<EventStatus?> = combine(base, extra) { st, more ->
        if (st == null) null else {
            // base 已含最近 PAGE_SIZE 条，extra 是更早的，直接拼在后面
            val known = st.records.map { it.id }.toSet()
            val older = more.filter { it.id !in known }
            st.copy(records = st.records + older)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 是否正在加载更早的记录，用于显示加载指示。 */
    val isLoadingMore: StateFlow<Boolean> = loading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 是否还有更早的记录可加载。 */
    fun hasMore(st: EventStatus?): Boolean =
        st != null && st.records.size < st.recordCount

    /**
     * 加载更早的一批。
     *
     * 以当前已加载数量为 offset，向数据库取 PAGE_SIZE 条。
     */
    fun loadMore() {
        val cur = status.value ?: return
        if (loading.value) return
        if (!hasMore(cur)) return

        viewModelScope.launch {
            loading.value = true
            runCatching {
                val page = repo.loadMoreRecords(eventId, PAGE_SIZE, cur.records.size)
                extra.value = extra.value + page
            }
            loading.value = false
        }
    }

    fun togglePin(onDone: () -> Unit) = viewModelScope.launch {
        repo.togglePin(false, eventId)
        onDone()
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        repo.setArchived(eventId, archived)
        onDone()
    }

    fun deleteEvent(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteEvent(eventId)
        onDone()
    }

    fun deleteRecord(r: Record, onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteRecord(r)
        onDone()
    }

    fun quickRecord(onDone: (String) -> Unit) = viewModelScope.launch {
        repo.quickRecord(eventId)
        onDone(repo.eventById(eventId)?.name ?: "")
    }
}
