package com.zwz.lifelog.data

import android.content.Context
import android.net.Uri
import com.zwz.lifelog.data.db.ArchivedEventRow
import com.zwz.lifelog.data.db.LifeLogDatabase
import com.zwz.lifelog.data.db.RecordEntity
import com.zwz.lifelog.data.db.toDomain
import com.zwz.lifelog.data.db.toEntity
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.domain.model.Template
import com.zwz.lifelog.domain.model.Templates
import com.zwz.lifelog.domain.usecase.StatusCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 对外统一的数据入口。UI 层只跟它打交道。
 *
 * 底层已从「全量 JSON 文件」换成 Room（SQLite）：
 *
 * | 场景 | JSON 时代 | Room 之后 |
 * |---|---|---|
 * | 记一笔 | 全量序列化两次（实测 ~131ms） | 单行插入（~1ms） |
 * | 首页列表 | 加载全部记录构造状态 | SQL 聚合，只算 COUNT/MIN/MAX |
 * | 搜索备注 | 全量遍历所有记录 | SQL LIKE，只返回命中的 id |
 *
 * 接口签名刻意保持不变，避免波及 30+ 个 UI 文件。
 */
class LifeLogRepository(private val context: Context) {

    private val db by lazy { LifeLogDatabase.get(context) }
    private val dao by lazy { db.dao() }
    private val json = JsonStore(context)

    val photos = PhotoStore(context)

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    /**
     * 首页列表状态流。
     *
     * **不加载任何记录**，只用一次 GROUP BY 拿到每个事件的
     * 条数与首尾时间，因此开销与总记录数无关。
     */
    fun statusesLite(): Flow<List<EventStatusLite>> =
        combine(dao.observeActiveEvents(), dao.observeAllStats()) { events, stats ->
            val byEvent = stats.associateBy { it.eventId }
            events.map { ev ->
                val s = byEvent[ev.id]
                StatusCalculator.computeLite(
                    event = ev.toDomain(),
                    count = s?.count ?: 0,
                    firstAsc = s?.firstTs,
                    lastAsc = s?.lastTs
                )
            }
        }

    /**
     * [statusesLite] 的一次性版本，供小组件使用。
     *
     * 小组件刷新是高频操作，走 SQL 聚合而不是全量加载记录。
     */
    suspend fun statusesLiteOnce(): List<EventStatusLite> = withContext(Dispatchers.IO) {
        val stats = dao.allStatsOnce().associateBy { it.eventId }
        dao.allEvents()
            .filter { !it.isArchived }
            .map { ev ->
                val s = stats[ev.id]
                StatusCalculator.computeLite(
                    event = ev.toDomain(),
                    count = s?.count ?: 0,
                    firstAsc = s?.firstTs,
                    lastAsc = s?.lastTs
                )
            }
            .sortedWith(
                compareByDescending<EventStatusLite> { it.event.isPinned }
                    .thenByDescending { it.ratio }
            )
    }

    /**
     * [statusOf] 的一次性版本，供单事件小组件使用。
     */
    suspend fun statusOfOnce(eventId: Long): EventStatus? = withContext(Dispatchers.IO) {
        val ev = dao.eventById(eventId) ?: return@withContext null
        val recs = dao.recordsOf(eventId).map { it.toDomain() }.sortedBy { it.timestamp }
        StatusCalculator.compute(ev.toDomain(), recs)
    }

    /**
     * 单个事件的完整状态（含时间线），**仅供详情页使用**。
     *
     * 只加载这一个事件的记录，不会牵连其他事件。
     */
    fun statusOf(eventId: Long): Flow<EventStatus?> =
        combine(
            dao.observeEvent(eventId),
            dao.observeRecordsOf(eventId)
        ) { ev, recs ->
            if (ev == null) null
            else StatusCalculator.compute(
                ev.toDomain(),
                recs.map { it.toDomain() }.sortedBy { it.timestamp }
            )
        }

    /** 归档事件列表。 */
    fun archivedEvents(): Flow<List<Event>> =
        dao.observeArchivedEvents().map { list -> list.map { it.toDomain() } }

    /**
     * 归档事件（含记录条数）。
     *
     * 设置页「已归档」用——显示条数是为了让用户在恢复或删除前
     * 知道这个事件到底记了多少东西，避免误删有重要记录的事件。
     */
    fun archivedWithCount(): Flow<List<ArchivedEventRow>> = dao.observeArchivedWithCount()

    /** 归档事件总数，设置页入口显示红点或小标题时用。 */
    fun archivedCountFlow(): Flow<Int> = dao.observeArchivedCount()

    /**
     * 详情页时间线分页。
     *
     * 替代「归档老记录」的方案：不改数据模型，
     * 年度回顾、导出、搜索都不受影响。
     */
    suspend fun recordsPage(eventId: Long, limit: Int, offset: Int): List<Record> =
        withContext(Dispatchers.IO) {
            dao.recordsPage(eventId, limit, offset).map { it.toDomain() }
        }

    /** 全量快照。导出、时间线、年度回顾等低频页面使用。 */
    suspend fun allRaw(): JsonStore.Snapshot = withContext(Dispatchers.IO) {
        JsonStore.Snapshot(
            events = dao.allEvents().map { it.toDomain() },
            records = dao.allRecords().map { it.toDomain() }
        )
    }

    /**
     * 原始快照流（时间线使用）。
     *
     * 注意：这会加载全部记录，**只适合确实需要全量数据的低频页面**。
     * 首页列表请用 [statusesLite]，设置页统计请用
     * [eventCountFlow] / [recordCountFlow]，别用它——
     * 为了显示两个数字而加载上万条记录是纯粹的浪费。
     */
    fun snapshotFlow(): Flow<JsonStore.Snapshot> = flow {
        // 任一表变化就重新拉一次全量
        combine(
            dao.observeAllEvents(),
            dao.observeAllStats()
        ) { _, _ -> Unit }.collect { emit(allRaw()) }
    }

    suspend fun eventById(id: Long): Event? =
        withContext(Dispatchers.IO) { dao.eventById(id)?.toDomain() }

    suspend fun recordsOf(eventId: Long): List<Record> =
        withContext(Dispatchers.IO) {
            dao.recordsOf(eventId).map { it.toDomain() }
        }

    /** 只取事件列表（小组件配置页等不需要记录的场景）。 */
    suspend fun allEvents(): List<Event> =
        withContext(Dispatchers.IO) { dao.allEvents().map { it.toDomain() } }

    /** 按 id 取单条记录（编辑记录时用，不必加载全部）。 */
    suspend fun recordById(id: Long): Record? =
        withContext(Dispatchers.IO) { dao.recordById(id)?.toDomain() }

    /**
     * 取指定时间段内的记录（年度回顾用）。
     *
     * 只查该年份的记录，而不是把所有记录读出来再按时间过滤。
     */
    suspend fun recordsBetween(from: Long, to: Long): List<Record> =
        withContext(Dispatchers.IO) { dao.recordsBetween(from, to).map { it.toDomain() } }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    /**
     * 返回命中关键词的事件 id 集合。
     *
     * 匹配范围：事件名、分类标签、记录备注全文。
     * 全部走 SQL，不把记录读进内存。
     */
    suspend fun searchMatchedEventIds(keyword: String): Set<Long> =
        withContext(Dispatchers.IO) {
            val kw = keyword.trim()
            if (kw.isBlank()) return@withContext emptySet()
            val byNote = dao.searchEventIdsByNote(kw).toSet()
            val byNameOrTag = dao.searchEventIdsByNameOrTag(kw).toSet()
            byNote + byNameOrTag
        }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /** 一键记录当前时间（桌面小组件、快捷方式走这里）。 */
    suspend fun quickRecord(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        val exists = dao.eventById(eventId) != null
        if (!exists) return@withContext false
        dao.insertRecord(
            RecordEntity(
                eventId = eventId,
                timestamp = System.currentTimeMillis(),
                loggedAt = System.currentTimeMillis()
            )
        )
        true
    }

    suspend fun addRecord(
        eventId: Long,
        timestamp: Long,
        note: String?,
        photoName: String?
    ): Long = withContext(Dispatchers.IO) {
        dao.insertRecord(
            RecordEntity(
                eventId = eventId,
                timestamp = timestamp,
                note = note,
                photoName = photoName,
                loggedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateRecord(record: Record) =
        withContext(Dispatchers.IO) { dao.updateRecord(record.toEntity()) }

    suspend fun deleteRecord(record: Record) = withContext(Dispatchers.IO) {
        if (record.photoName != null) photos.delete(record.photoName)
        dao.deleteRecord(record.id)
    }

    suspend fun upsertEvent(event: Event): Long = withContext(Dispatchers.IO) {
        if (event.id == 0L) {
            dao.upsertEvent(event.copy(createdAt = System.currentTimeMillis()).toEntity())
        } else {
            dao.upsertEvent(event.toEntity())
            event.id
        }
    }

    suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        // 记录的级联删除由外键 onDelete = CASCADE 负责，
        // 但照片文件在私有目录，得手动清理
        dao.recordsOf(eventId)
            .mapNotNull { it.photoName }
            .forEach { photos.delete(it) }
        dao.deleteEvent(eventId)
    }

    suspend fun togglePin(unused: Boolean, id: Long) =
        withContext(Dispatchers.IO) { dao.togglePin(id) }

    suspend fun setArchived(eventId: Long, archived: Boolean) =
        withContext(Dispatchers.IO) { dao.setArchived(eventId, archived) }

    /**
     * 保存手动排序。
     *
     * @param orderedIds 拖拽后的**完整**顺序（当前可见范围内），
     *                   下标即新的 sortOrder。
     *
     * 为什么传完整列表而不是单个移动：拖拽过程中列表可能连续变化，
     * 只提交「谁移动了」容易与实际顺序脱节。
     */
    suspend fun saveSortOrder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        dao.updateSortOrders(orderedIds)
    }

    /** 导入模板：只导入当前还不存在的同名事件。 */
    suspend fun importTemplates(names: Set<String>): Int = withContext(Dispatchers.IO) {
        insertTemplates(Templates.ALL.filter { it.name in names })
    }

    /**
     * 导入外部模板文件里的模板。
     *
     * 同名事件跳过，只返回实际新增的条数供 UI 提示。
     */
    suspend fun importExternalTemplates(templates: List<Template>): Int =
        withContext(Dispatchers.IO) { insertTemplates(templates) }

    private suspend fun insertTemplates(templates: List<Template>): Int {
        val existing = dao.allEvents().map { it.name }.toSet()
        val maxSort = dao.allEvents().maxOfOrNull { it.sortOrder } ?: 0
        var added = 0
        templates
            .filter { it.name !in existing }
            .forEachIndexed { index, t ->
                dao.upsertEvent(
                    Event(
                        name = t.name,
                        emoji = t.emoji,
                        targetDays = t.targetDays,
                        tag = t.tag,
                        sortOrder = maxSort + index + 1
                    ).toEntity()
                )
                added++
            }
        return added
    }

    // ------------------------------------------------------------------
    // 导入导出
    // ------------------------------------------------------------------

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        json.serialize(allRaw())
    }

    suspend fun importJson(text: String, merge: Boolean): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snap = json.parse(text)
                if (merge) {
                    // 同名事件合并，其余新增；记录按 (事件名, 时间戳) 去重
                    val existing = dao.allEvents()
                    val byName = existing.associateBy { it.name }.toMutableMap()

                    snap.events.forEach { incoming ->
                        if (byName.containsKey(incoming.name)) return@forEach
                        val newId = dao.upsertEvent(incoming.copy(id = 0L).toEntity())
                        byName[incoming.name] =
                            dao.eventById(newId) ?: return@forEach
                    }

                    val existingKeys = dao.allRecords()
                        .map { Pair(it.eventId, it.timestamp) }
                        .toMutableSet()

                    snap.records.forEach { r ->
                        val sourceName = snap.events.firstOrNull { it.id == r.eventId }?.name
                        val targetId = sourceName?.let { byName[it]?.id } ?: return@forEach
                        val key = Pair(targetId, r.timestamp)
                        if (key !in existingKeys) {
                            existingKeys.add(key)
                            dao.insertRecord(
                                r.copy(id = 0L, eventId = targetId).toEntity()
                            )
                        }
                    }
                } else {
                    // 覆盖模式：清空后重建（外键是 CASCADE，清表即可）
                    db.clearAllTables()
                    snap.events.forEach { dao.upsertEvent(it.toEntity()) }
                    snap.records.forEach { dao.insertRecord(it.toEntity()) }
                }
                snap.events.size
            }
        }

    suspend fun savePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        photos.saveFromUri(uri)
    }

    /**
     * 启动时调用，保留以满足既有调用点（小组件、Application）。
     *
     * **不做数据迁移**：本项目尚在试用阶段，没有需要保留的历史数据，
     * 从 JSON 搬家的逻辑已按使用者要求移除。Room 自身在首次访问时才建库，
     * 因此这里不需要做任何事。
     */
    suspend fun load() = withContext(Dispatchers.IO) { Unit }

    /** 事件总数流（设置页统计）。 */
    fun eventCountFlow(): Flow<Int> = dao.observeEventCount()

    /** 记录总数流（设置页统计）。 */
    fun recordCountFlow(): Flow<Int> = dao.observeRecordCount()
}
