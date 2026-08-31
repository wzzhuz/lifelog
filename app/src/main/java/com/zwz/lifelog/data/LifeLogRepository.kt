package com.zwz.lifelog.data

import android.content.Context
import android.net.Uri
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.domain.model.Templates
import com.zwz.lifelog.domain.usecase.StatusCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 对外统一的数据入口。UI 层只跟它打交道。
 */
class LifeLogRepository(private val context: Context) {

    private val store = JsonStore(context)
    val photos = PhotoStore(context)

    fun statuses(): Flow<List<EventStatus>> = store.snapshot.map { snap ->
        val byEvent = snap.records.groupBy { it.eventId }
        snap.events
            .filter { !it.isArchived }
            .map { ev ->
                StatusCalculator.compute(ev, (byEvent[ev.id] ?: emptyList()).sortedBy { it.timestamp })
            }
    }

    fun archivedEvents(): Flow<List<Event>> = store.snapshot.map { snap ->
        snap.events.filter { it.isArchived }
    }

    suspend fun allRaw() = store.snapshot.value

    /** 原始快照流（时间线、年度回顾等需要跨事件统计的页面使用）。 */
    fun snapshotFlow() = store.snapshot

    suspend fun eventById(id: Long): Event? =
        store.snapshot.value.events.firstOrNull { it.id == id }

    suspend fun recordsOf(eventId: Long): List<Record> =
        store.snapshot.value.records.filter { it.eventId == eventId }.sortedByDescending { it.timestamp }

    /** 一键记录当前时间（桌面小组件、快捷方式走这里）。 */
    suspend fun quickRecord(eventId: Long): Boolean {
        val ev = eventById(eventId) ?: return false
        store.update { snap ->
            val newId = (snap.records.maxOfOrNull { it.id } ?: 0L) + 1L
            snap.copy(
                records = (snap.records + Record(id = newId, eventId = ev.id)).sortedBy { it.timestamp }
            )
        }
        return true
    }

    suspend fun addRecord(eventId: Long, timestamp: Long, note: String?, photoName: String?): Long {
        var newId = 0L
        store.update { snap ->
            newId = (snap.records.maxOfOrNull { it.id } ?: 0L) + 1L
            snap.copy(
                records = (snap.records + Record(
                    id = newId,
                    eventId = eventId,
                    timestamp = timestamp,
                    note = note,
                    photoName = photoName
                )).sortedBy { it.timestamp }
            )
        }
        return newId
    }

    suspend fun updateRecord(record: Record) {
        store.update { snap ->
            snap.copy(records = snap.records.map { if (it.id == record.id) record else it }
                .sortedBy { it.timestamp })
        }
    }

    suspend fun deleteRecord(record: Record) {
        if (record.photoName != null) photos.delete(record.photoName)
        store.update { snap -> snap.copy(records = snap.records.filterNot { it.id == record.id }) }
    }

    suspend fun upsertEvent(event: Event): Long {
        var id = event.id
        store.update { snap ->
            if (event.id == 0L) {
                id = (snap.events.maxOfOrNull { it.id } ?: 0L) + 1L
                snap.copy(events = snap.events + event.copy(id = id, createdAt = System.currentTimeMillis()))
            } else {
                snap.copy(events = snap.events.map { if (it.id == event.id) event else it })
            }
        }
        return id
    }

    suspend fun deleteEvent(eventId: Long) {
        val recs = store.snapshot.value.records.filter { it.eventId == eventId }
        recs.forEach { if (it.photoName != null) photos.delete(it.photoName) }
        store.update { snap ->
            snap.copy(
                events = snap.events.filterNot { it.id == eventId },
                records = snap.records.filterNot { it.eventId == eventId }
            )
        }
    }

    suspend fun togglePin(eventId: Boolean, id: Long) {
        store.update { snap ->
            snap.copy(events = snap.events.map {
                if (it.id == id) it.copy(isPinned = !it.isPinned) else it
            })
        }
    }

    suspend fun setArchived(eventId: Long, archived: Boolean) {
        store.update { snap ->
            snap.copy(events = snap.events.map {
                if (it.id == eventId) it.copy(isArchived = archived) else it
            })
        }
    }

    /** 导入模板：只导入当前还不存在的同名事件。 */
    suspend fun importTemplates(names: Set<String>): Int {
        var added = 0
        store.update { snap ->
            var maxId = snap.events.maxOfOrNull { it.id } ?: 0L
            val existing = snap.events.map { it.name }.toSet()
            val toAdd = Templates.ALL.filter { it.name in names && it.name !in existing }
                .mapIndexed { index, t ->
                    Event(
                        id = ++maxId,
                        name = t.name,
                        emoji = t.emoji,
                        targetDays = t.targetDays,
                        tag = t.tag,
                        sortOrder = (snap.events.maxOfOrNull { it.sortOrder } ?: 0) + index + 1
                    )
                }
            added = toAdd.size
            snap.copy(events = snap.events + toAdd)
        }
        return added
    }

    suspend fun exportJson(): String = store.serialize(store.snapshot.value)

    suspend fun importJson(text: String, merge: Boolean): Result<Int> = runCatching {
        val snap = store.parse(text)
        if (merge) {
            store.merge(snap.events, snap.records)
        } else {
            store.replace(snap.events, snap.records)
        }
        snap.events.size
    }

    suspend fun savePhoto(uri: Uri): String? = photos.saveFromUri(uri)

    suspend fun load() = store.load()
}
