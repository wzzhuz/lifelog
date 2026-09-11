package com.zwz.lifelog.data

import android.content.Context
import android.util.Log
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import com.zwz.lifelog.domain.model.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 极简 JSON 持久化。
 *
 * 刻意不引入 Room：本项目数据量极小（个人生活琐事，几年下来通常也只有几百条记录），
 * 全量读写 JSON 的性能完全够用，同时省掉注解处理器，显著降低 CI 首次编译的失败概率。
 *
 * 写入采用「先写临时文件再原子改名」，避免写一半崩溃导致数据文件损坏。
 */
class JsonStore(private val context: Context) {

    companion object {
        private const val TAG = "JsonStore"
        private const val FILE_NAME = "lifelog.json"
        private const val TMP_NAME = "lifelog.json.tmp"
        private const val VERSION = 1
    }

    data class Snapshot(
        val events: List<Event>,
        val records: List<Record>
    )

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(Snapshot(emptyList(), emptyList()))
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val tmpFile: File get() = File(context.filesDir, TMP_NAME)

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snap = runCatching {
                if (!file.exists()) return@runCatching Snapshot(emptyList(), emptyList())
                val text = file.readText()
                if (text.isBlank()) return@runCatching Snapshot(emptyList(), emptyList())
                parse(text)
            }.getOrElse { e ->
                Log.e(TAG, "数据文件解析失败，尝试从备份恢复", e)
                restoreFromBackup() ?: Snapshot(emptyList(), emptyList())
            }
            _snapshot.value = snap
        }
    }

    suspend fun update(mutator: (Snapshot) -> Snapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = mutator(_snapshot.value)
            _snapshot.value = next
            persistLocked(next)
        }
    }

    /** 供备份恢复使用：整体替换。 */
    suspend fun replace(events: List<Event>, records: List<Record>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = Snapshot(events, records)
            _snapshot.value = next
            persistLocked(next)
        }
    }

    /** 把另一份数据合并进来：同名事件合并记录（按 timestamp 去重），其余新增。 */
    suspend fun merge(events: List<Event>, records: List<Record>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cur = _snapshot.value
            val byName = cur.events.associateBy { it.name }.toMutableMap()
            val newEvents = mutableListOf<Event>()
            var maxId = cur.events.maxOfOrNull { it.id } ?: 0L

            events.forEach { incoming ->
                val existing = byName[incoming.name]
                if (existing != null) {
                    // 同名事件：保留本地定义，合并记录
                    Unit
                } else {
                    val reId = incoming.copy(id = ++maxId)
                    newEvents.add(reId)
                    byName[reId.name] = reId
                }
            }

            // 记录按 (eventName, timestamp) 去重后重新映射 id
            val nameToId = byName.mapValues { it.value.id }
            val existingKeys = cur.records.map { Pair(it.eventId, it.timestamp) }.toMutableSet()
            var maxRecId = cur.records.maxOfOrNull { it.id } ?: 0L
            val merged = cur.records.toMutableList()

            records.forEach { r ->
                val sourceName = events.firstOrNull { it.id == r.eventId }?.name ?: return@forEach
                val targetId = nameToId[sourceName] ?: return@forEach
                val key = Pair(targetId, r.timestamp)
                if (key !in existingKeys) {
                    existingKeys.add(key)
                    merged.add(r.copy(id = ++maxRecId, eventId = targetId))
                }
            }

            val next = Snapshot(
                events = (cur.events + newEvents),
                records = merged.sortedBy { it.timestamp }
            )
            _snapshot.value = next
            persistLocked(next)
        }
    }

    private fun persistLocked(snap: Snapshot) {
        try {
            tmpFile.writeText(serialize(snap))
            if (tmpFile.length() > 0) {
                if (file.exists()) file.delete()
                if (!tmpFile.renameTo(file)) {
                    tmpFile.copyTo(file, overwrite = true)
                    tmpFile.delete()
                }
            }
            maybeAutoBackupLocked(snap)
        } catch (e: IOException) {
            Log.e(TAG, "写入失败", e)
        }
    }

    /** 每次写入后顺带维护最多 4 份滚动备份。 */
    private fun maybeAutoBackupLocked(snap: Snapshot) {
        try {
            val dir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
            val text = serialize(snap)
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            val target = File(dir, "autobackup-$stamp.json")
            if (!target.exists()) target.writeText(text)
            val all = dir.listFiles { f -> f.name.startsWith("autobackup-") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() } ?: return
            if (all.size > 4) all.drop(4).forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "自动备份失败（不影响主流程）", e)
        }
    }

    private fun restoreFromBackup(): Snapshot? {
        val dir = File(context.filesDir, "backups")
        val latest = dir.listFiles { f -> f.name.startsWith("autobackup-") }
            ?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { parse(latest.readText()) }.getOrNull()
    }

    // ---------- 序列化 ----------

    fun serialize(snap: Snapshot): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "com.zwz.lifelog")

        val evArr = JSONArray()
        snap.events.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("name", e.name)
            o.put("emoji", e.emoji)
            o.put("colorArgb", e.colorArgb)
            o.put("kind", e.kind.name)
            if (e.targetDays != null) o.put("targetDays", e.targetDays)
            if (e.timesPerDay != null) o.put("timesPerDay", e.timesPerDay)
            if (e.parentId != null) o.put("parentId", e.parentId)
            if (e.tag != null) o.put("tag", e.tag)
            if (e.note != null) o.put("note", e.note)
            o.put("isPinned", e.isPinned)
            o.put("isArchived", e.isArchived)
            o.put("sortOrder", e.sortOrder)
            o.put("createdAt", e.createdAt)
            evArr.put(o)
        }
        root.put("events", evArr)

        val recArr = JSONArray()
        snap.records.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("eventId", r.eventId)
            o.put("timestamp", r.timestamp)
            if (r.note != null) o.put("note", r.note)
            if (r.photoName != null) o.put("photoName", r.photoName)
            o.put("loggedAt", r.loggedAt)
            recArr.put(o)
        }
        root.put("records", recArr)
        return root.toString(2)
    }

    fun parse(text: String): Snapshot {
        val root = JSONObject(text)
        val events = mutableListOf<Event>()
        val evArr = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until evArr.length()) {
            val o = evArr.getJSONObject(i)
            val td = if (o.has("targetDays")) o.getInt("targetDays") else null
            events.add(
                Event(
                    id = o.optLong("id", (i + 1).toLong()),
                    name = o.optString("name", "未命名"),
                    emoji = o.optString("emoji", "\uD83D\uDCCC"),
                    colorArgb = o.optInt("colorArgb", 0xFF2F6FED.toInt()),
                    kind = EventKind.of(o.optString("kind", null)),
                    targetDays = td,
                    timesPerDay = if (o.has("timesPerDay")) o.optInt("timesPerDay") else null,
                    parentId = if (o.has("parentId")) o.optLong("parentId") else null,
                    tag = o.optString("tag", "").ifBlank { null },
                    note = o.optString("note", "").ifBlank { null },
                    isPinned = o.optBoolean("isPinned", false),
                    isArchived = o.optBoolean("isArchived", false),
                    sortOrder = o.optInt("sortOrder", 0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val records = mutableListOf<Record>()
        val recArr = root.optJSONArray("records") ?: JSONArray()
        for (i in 0 until recArr.length()) {
            val o = recArr.getJSONObject(i)
            records.add(
                Record(
                    id = o.optLong("id", (i + 1).toLong()),
                    eventId = o.optLong("eventId", 0L),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    note = o.optString("note", "").ifBlank { null },
                    photoName = o.optString("photoName", "").ifBlank { null },
                    loggedAt = o.optLong("loggedAt", o.optLong("timestamp", 0L))
                )
            )
        }
        return Snapshot(events, records.sortedBy { it.timestamp })
    }
}
