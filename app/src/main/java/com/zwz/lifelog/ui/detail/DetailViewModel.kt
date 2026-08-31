package com.zwz.lifelog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Record
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repo: LifeLogRepository,
    private val eventId: Long
) : ViewModel() {

    // 只查这一个事件及其记录，不牵连其他事件的历史数据。
    // 早期实现是 repo.statuses().map { firstOrNull { ... } }，
    // 那会把所有事件的全部记录都加载一遍——详情页只为看一个事件，纯属浪费。
    val status: StateFlow<EventStatus?> = repo.statusOf(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
