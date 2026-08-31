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

    val status: StateFlow<EventStatus?> = repo.statuses()
        .map { list -> list.firstOrNull { it.event.id == eventId } }
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
