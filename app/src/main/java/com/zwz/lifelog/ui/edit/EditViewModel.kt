package com.zwz.lifelog.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditViewModel(
    private val repo: LifeLogRepository,
    private val eventId: Long
) : ViewModel() {

    private val _draft = MutableStateFlow(Event(id = eventId, name = ""))
    val draft: StateFlow<Event> = _draft

    private val _loaded = MutableStateFlow(eventId == 0L)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        if (eventId != 0L) {
            viewModelScope.launch {
                repo.eventById(eventId)?.let { _draft.value = it }
                _loaded.value = true
            }
        }
    }

    fun patch(block: (Event) -> Event) { _draft.value = block(_draft.value) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val e = _draft.value
        if (e.name.isBlank()) return@launch
        repo.upsertEvent(e.copy(name = e.name.trim()))
        onDone()
    }

    companion object {
        val PALETTE = listOf(
            0xFF2F6FED, 0xFF16A34A, 0xFFE08C00, 0xFFE5484D,
            0xFF8B5CF6, 0xFF06B6D4, 0xFFEC4899, 0xFF64748B
        ).map { it.toInt() }

        val EMOJIS = listOf(
            "\u2702\uFE0F", "\uD83D\uDECF\uFE0F", "\uD83C\uDFE5", "\uD83E\uDDB7",
            "\uD83D\uDC8A", "\uD83E\uDDA5", "\uD83E\uDDF4", "\uD83D\uDE97",
            "\uD83D\uDC3E", "\uD83E\uDDEB", "\uD83D\uDCCC", "\u2764\uFE0F"
        )
    }
}
