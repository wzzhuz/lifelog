package com.zwz.lifelog.data

import android.content.Context
import android.net.Uri
import com.zwz.lifelog.data.db.ArchivedEventRow
import androidx.room.withTransaction
import com.zwz.lifelog.data.db.LifeLogDatabase
import com.zwz.lifelog.data.db.RecordEntity
import com.zwz.lifelog.data.db.toDomain
import com.zwz.lifelog.data.db.toEntity
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.domain.model.Template
import com.zwz.lifelog.domain.model.Templates
import com.zwz.lifelog.domain.usecase.StatusCalculator
import com.zwz.lifelog.ui.timeline.TimelineRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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

    /**
     * 确保派生列已经按当前 schema 重算过。
     *
     * 数据库迁移只**加列**，派生值仍停留在默认的 0/null——
     * 不重算的话，已有记录的事件会全部显示成「0 次、从没记过」。
     *
     * 用 SharedPreferences 记一个版本号，只在版本变化时重算一次；
     * 之后每次启动只是读一次整数，开销可忽略。
     *
     * 幂等且**不碰 records 表**，因此反复调用也安全。
     */
    private fun ensureDerivedReady() {
        val prefs = context.getSharedPreferences("lifelog_derived", Context.MODE_PRIVATE)
        val done = prefs.getInt("version", 0)
        if (done >= DERIVED_SCHEMA_VERSION) return
        // 记录里可能已有数据，必须在 IO 线程做
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                dao.recomputeDerived(startOfToday())
                prefs.edit().putInt("version", DERIVED_SCHEMA_VERSION).apply()
            }
        }
    }

    init {
        // 构造时就跑：首页第一次读到的必须是正确的值，
        // 否则会先闪一下「0 次」再跳到真实值。
        runCatching { ensureDerivedReady() }
    }

    companion object {
        /** 派生列的 schema 版本。加列或改算法时 +1，触发一次重算。 */
        private const val DERIVED_SCHEMA_VERSION = 1
    }

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    /**
     * 今天 00:00 的时间戳（设备时区）。
     *
     * 频次型事件（一天三次的药）的状态只按**当日**完成次数算，
     * 跨天的那一下必须重新取一次起点，否则昨天的次数会算到今天头上。
     */
    private fun startOfToday(): Long =
        java.time.LocalDate.now(java.time.ZoneId.systemDefault())
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /**
     * 每分钟发一次的「当天起点」。
     *
     * 只有在**跨天**那一分钟值会变，[distinctUntilChanged] 挡掉其余 59 次，
     * 因此一天只多出一次轻量 GROUP BY。
     */
    private val dayStartFlow: Flow<Long> = flow {
        while (true) {
            emit(startOfToday())
            delay(60_000L)
        }
    }.distinctUntilChanged()

    /** 各事件当日已记录条数。走 SQL 聚合，不读记录行。 */
    private fun todayCounts(): Flow<Map<Long, Int>> =
        dayStartFlow
            .flatMapLatest { dayStart -> dao.observeTodayCounts(dayStart) }
            .map { list -> list.associateBy({ it.eventId }, { it.count }) }

    /**
     * 今日 00:00 的「日序」（`yyyyMMdd`）。
     *
     * 用它判断 `todayCount` 是否过期：App 跨越零点未打开时，
     * 库里存的还是昨天的次数，读时发现日序不符就按 0 处理。
     */
    private fun dayKeyOf(ts: Long): Int {
        val d = java.time.LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(ts),
            java.time.ZoneId.systemDefault()
        )
        return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    /**
     * 当天已完成的次数。
     *
     * **读时校正**：`lastRecordDay` 不等于今天就返回 0，且不写回。
     * 写回会产生一次多余的数据库写入并触发列表刷新，
     * 而"跨天"这件事本身不需要落库——下次写入自然会更新。
     */
    private fun todayCountOf(ev: Event): Int {
        val today = dayKeyOf(System.currentTimeMillis())
        return if (ev.lastRecordDay == today) ev.todayCount else 0
    }

    /**
     * 把事件拼成首页状态。
     *
     * **只读 events 单表**：条数、首尾时间、当日次数全部来自派生列，
     * 不再对 records 做 GROUP BY。首页复杂度因此是 O(事件数)，
     * 与记录总量解耦——这是本次性能改造的主要收益。
     *
     * 另外两件事：
     * - 给子事件补上疗程名（列表副标题末尾显示）
     * - 疗程（[EventKind.COURSE]）用子事件重算状态
     */
    private fun buildLite(events: List<Event>): List<EventStatusLite> {
        val nameOf = events.associateBy { it.id }
        val now = System.currentTimeMillis()

        val lite = events.map { ev ->
            val st = StatusCalculator.computeLite(
                event = ev,
                count = ev.recordCount,
                firstAsc = ev.firstTs,
                lastAsc = ev.lastTs,
                now = now,
                doneToday = todayCountOf(ev)
            )
            val parentName = ev.parentId?.let { nameOf[it]?.name }
            if (parentName == null) st else st.copy(parentName = parentName)
        }

        // 一次 groupBy 取代「对每个疗程 filter 一遍全表」（原本是 O(n²)）
        val childrenOf = lite.groupBy { it.event.parentId }
        return lite.map { st ->
            if (st.event.kind != EventKind.COURSE) st
            else StatusCalculator.aggregateCourseLite(
                st,
                childrenOf[st.event.id] ?: emptyList()
            )
        }
    }

    /**
     * 首页列表状态流。
     *
     * **单表查询**：状态所需的一切都在 events 的派生列里，
     * 不碰 records 表，因此开销只与事件数有关（几十个），与记录总量无关。
     */
    fun statusesLite(): Flow<List<EventStatusLite>> =
        dao.observeActiveEvents().map { list -> buildLite(list.map { it.toDomain() }) }

    /**
     * [statusesLite] 的一次性版本，供小组件使用。
     *
     * 同样只读 events 单表——小组件刷新是高频操作，
     * 走全表聚合的话每次刷新都要扫一遍 records。
     */
    suspend fun statusesLiteOnce(): List<EventStatusLite> = withContext(Dispatchers.IO) {
        val events = dao.allEvents().filter { !it.isArchived }.map { it.toDomain() }
        buildLite(events)
            .sortedWith(
                compareByDescending<EventStatusLite> { it.event.isPinned }
                    .thenByDescending { it.ratio }
            )
    }

    /**
     * [statusOf] 的一次性版本，供单事件小组件使用。
     *
     * 小组件只需要状态数字，不需要时间线，因此**不加载记录**，
     * 全部从派生列取。
     */
    suspend fun statusOfOnce(eventId: Long): EventStatus? = withContext(Dispatchers.IO) {
        val ev = dao.eventById(eventId) ?: return@withContext null
        val domain = ev.toDomain()
        StatusCalculator.computeLite(
            event = domain,
            count = domain.recordCount,
            firstAsc = domain.firstTs,
            lastAsc = domain.lastTs,
            doneToday = todayCountOf(domain)
        ).toEventStatus()
    }

    /**
     * 单个事件的完整状态（含时间线），**仅供详情页使用**。
     *
     * 只加载这一个事件的记录，不会牵连其他事件。
     */
    /**
     * 详情页分页状态。
     *
     * 与 [statusOf] 的区别：记录只取最近 [limit] 条，
     * 而统计信息（次数 / 平均间隔 / 预测）走 SQL 聚合，仍基于全量数据。
     *
     * **为什么统计不能用分页结果算**：
     * 分页后内存里只有 20 条，拿它算「累计次数」会得到 20 而不是真实总数，
     * 「平均间隔」也会因为只覆盖最近一段而失真。
     */
    fun statusOfPaged(eventId: Long, limit: Int): Flow<EventStatus?> {
        // 子事件状态：只读 events 单表（几十行），非疗程时自然返回空列表。
        // 关键是不再订阅 records 的全表聚合——那才是详情页进出的耗时来源。
        val children: Flow<List<EventStatusLite>> = childrenStatuses(eventId)

        // 统计值改从 events 的派生列读，不再订阅 records 的聚合
        return combine(
            dao.observeEvent(eventId),
            dao.observeRecordsPage(eventId, limit),
            children
        ) { ev, recs, kids ->
            if (ev == null) null
            else {
                val domain = ev.toDomain()
                StatusCalculator.computePaged(
                    event = domain,
                    recordsDesc = recs.map { it.toDomain() },
                    count = domain.recordCount,
                    firstAsc = domain.firstTs,
                    lastAsc = domain.lastTs,
                    doneToday = todayCountOf(domain),
                    children = kids
                )
            }
        }
    }


    /**
     * 疗程的子事件状态，详情页「添加子事件」后自动刷新。
     *
     * 只读 events 单表——子事件的数量本来就不多，
     * 没必要为它们去扫 records。
     */
    fun childrenStatuses(parentId: Long): Flow<List<EventStatusLite>> =
        dao.observeActiveChildren(parentId).map { kids ->
            kids.map { k ->
                val d = k.toDomain()
                StatusCalculator.computeLite(
                    event = d,
                    count = d.recordCount,
                    firstAsc = d.firstTs,
                    lastAsc = d.lastTs,
                    doneToday = todayCountOf(d)
                )
            }
        }

    /**
     * 结束疗程：疗程与其全部子事件一并归档。
     *
     * 一个事务内完成，避免中间态被 Flow 发出去。
     */
    suspend fun endCourse(parentId: Long) =
        withContext(Dispatchers.IO) { dao.setArchivedForCourse(parentId, true) }

    /** 恢复疗程：连同子事件一起回到首页。 */
    suspend fun restoreCourse(parentId: Long) =
        withContext(Dispatchers.IO) { dao.setArchivedForCourse(parentId, false) }

    /** 疗程（含子事件）的记录总数，结束前的确认文案要用。 */
    suspend fun courseRecordCount(parentId: Long): Int = withContext(Dispatchers.IO) {
        val self = dao.statsOf(parentId)?.count ?: 0
        val kids = dao.allChildren(parentId).sumOf { dao.statsOf(it.id)?.count ?: 0 }
        self + kids
    }

    /** 疗程下未归档子事件的数量。 */
    suspend fun activeChildCount(parentId: Long): Int =
        withContext(Dispatchers.IO) { dao.activeChildren(parentId).size }

    /** 载入更早的一批记录。返回本次新载入的条数。 */
    suspend fun loadMoreRecords(eventId: Long, limit: Int, offset: Int): List<Record> =
        withContext(Dispatchers.IO) {
            dao.recordsPage(eventId, limit, offset).map { it.toDomain() }
        }

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
        // 任一表变化就重新拉一次全量。
        //
        // ⚠️ 这条会**全表读 records**（无 LIMIT），是实测里最慢的查询
        // （200 万条时 2.5 秒）。时间线已改为走 [timelinePage] 分页，
        // 这里仅供导出等必须全量的低频场景使用，**不要接到 UI 上**。
        combine(
            dao.observeAllEvents(),
            dao.observeAllStats()
        ) { _, _ -> Unit }.collect { emit(allRaw()) }
    }

    /**
     * 全局时间线的一页。
     *
     * 实测（200 万条）：首页 0.03ms / 第 100 页 0.12ms，
     * 而全表读要 2.5 秒。时间线因此改为按需翻页。
     *
     * 事件名以 Map 一并返回，避免 UI 侧每行再查一次库。
     */
    suspend fun timelinePage(limit: Int, offset: Int): List<TimelineRow> =
        withContext(Dispatchers.IO) {
            val recs = dao.recordsGlobalPage(limit, offset)
            if (recs.isEmpty()) return@withContext emptyList()
            val nameOf = dao.allEvents().associateBy { it.id }
            recs.mapNotNull { r ->
                val ev = nameOf[r.eventId] ?: return@mapNotNull null
                TimelineRow(
                    record = r.toDomain(),
                    eventId = ev.id,
                    eventName = ev.name,
                    emoji = ev.emoji
                )
            }
        }

    /** 记录总数，供时间线判断「还有没有更早的」。 */
    suspend fun recordCount(): Int = withContext(Dispatchers.IO) {
        dao.recordCountOnce()
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
    /**
     * 插入一条记录并推进派生列，**同一个事务**内完成。
     *
     * 所有写记录的入口都必须走这里，否则派生列会与实际记录不一致。
     * 万一漏了一处，`recomputeDerived()` 能一键修复。
     */
    private suspend fun insertRecordAndBump(entity: RecordEntity): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = dao.insertRecord(entity)
            val day = dayKeyOf(entity.timestamp)
            // lastTs 取 MAX：补录一条更早的记录不应该把「上次」往前挪
            val cur = dao.eventById(entity.eventId)
            val lastTs = maxOf(cur?.lastTs ?: 0L, entity.timestamp)
            dao.bumpDerived(
                eventId = entity.eventId,
                ts = entity.timestamp,
                lastTs = lastTs,
                day = day
            )
            id
        }
    }

    suspend fun quickRecord(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        val exists = dao.eventById(eventId) != null
        if (!exists) return@withContext false
        insertRecordAndBump(
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
    ): Long = insertRecordAndBump(
        RecordEntity(
            eventId = eventId,
            timestamp = timestamp,
            note = note,
            photoName = photoName,
            loggedAt = System.currentTimeMillis()
        )
    )

    /**
     * 全量重算派生列（幂等）。
     *
     * 用于迁移后、导入后，以及「怀疑数据不一致」时的手动修复。
     * 一次 UPDATE 完成，记录表不动，因此不会丢任何数据。
     */
    suspend fun recomputeDerived() = withContext(Dispatchers.IO) {
        dao.recomputeDerived(startOfToday())
    }

    suspend fun updateRecord(record: Record) =
        withContext(Dispatchers.IO) { dao.updateRecord(record.toEntity()) }

    suspend fun deleteRecord(record: Record) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (record.photoName != null) photos.delete(record.photoName)
            dao.deleteRecord(record.id)
            // 不靠减法推 lastTs（容易算错），删完直接重取真实值
            refreshDerived(record.eventId)
        }
    }

    /**
     * 按 records 的真实情况重写某个事件的派生列。
     *
     * 删除记录后调用：删掉的可能正是最后一条，
     * lastTs 得回退到前一条，这是减法推不出来的。
     */
    private suspend fun refreshDerived(eventId: Long) {
        val st = dao.statsOfEvent(eventId)
        val last = st.lastTs
        val day = last?.let { dayKeyOf(it) }
        val today = last?.let { dao.countSince(eventId, startOfToday()) } ?: 0
        dao.setDerived(
            eventId = eventId,
            count = st.count,
            lastTs = last,
            day = day,
            todayCount = today
        )
    }

    /**
     * 生成一个不与既有疗程重名的名字。
     *
     * 同一天第二次感冒会撞名（「感冒 9月11日」），追加序号区分。
     * **含已归档**一起查：归档页里出现两个一模一样的名字同样难分辨。
     */
    suspend fun uniqueCourseName(base: String): String = withContext(Dispatchers.IO) {
        val taken = dao.allEvents().map { it.name }.toSet()
        if (base !in taken) return@withContext base
        var n = 2
        while ("$base · $n" in taken) n++
        "$base · $n"
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
        val ev = dao.eventById(eventId)
        val ids = if (ev != null && EventKind.of(ev.kind) == EventKind.COURSE) {
            // 疗程：子事件及其记录一并清除，否则会留下指向已删除疗程的孤儿
            dao.allChildren(eventId).map { it.id } + eventId
        } else {
            listOf(eventId)
        }
        // 只查 photoName 这一列：整个事件的记录可能有几万条，
        // 全量读出来只为筛几张照片太浪费（实测单事件 10 万条时 71.9ms）
        ids.forEach { id ->
            dao.photoNamesOf(id).forEach { photos.delete(it) }
        }
        dao.deleteEventWithChildren(eventId)
    }

    suspend fun togglePin(unused: Boolean, id: Long) =
        withContext(Dispatchers.IO) { dao.togglePin(id) }

    /**
     * 归档 / 恢复。
     *
     * 疗程走 [dao.setArchivedForCourse]：父与子一起改，
     * 否则会出现「疗程归档了、药还挂在首页」的半吊子状态。
     */
    suspend fun setArchived(eventId: Long, archived: Boolean) =
        withContext(Dispatchers.IO) {
            val ev = dao.eventById(eventId)
            if (ev != null && EventKind.of(ev.kind) == EventKind.COURSE) {
                dao.setArchivedForCourse(eventId, archived)
            } else {
                dao.setArchived(eventId, archived)
            }
        }

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

    /**
     * 只对**顶层事件**按名去重。
     *
     * 子事件（parentId != null）不参与全局唯一性判断——
     * 不同疗程本就该有同名药，第二次生病时还要能再从预设里加一个「退烧药」。
     */
    private suspend fun insertTemplates(templates: List<Template>): Int {
        val existing = dao.allEvents().filter { it.parentId == null }.map { it.name }.toSet()
        val maxSort = dao.allEvents().maxOfOrNull { it.sortOrder } ?: 0
        var added = 0
        templates
            .filter { it.name !in existing }
            .forEachIndexed { index, t ->
                dao.upsertEvent(
                    Event(
                        name = t.name,
                        emoji = t.emoji,
                        kind = t.kind,
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
                        // 合并后 id 会被重排，parentId 指向的旧 id 不再可靠。
                        // 层级关系无法安全迁移时降级为独立事件，
                        // 宁可少一层关系，也不能挂到一个不相干的疗程下。
                        val newId = dao.upsertEvent(
                            incoming.copy(id = 0L, parentId = null).toEntity()
                        )
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
                    // 记录被 CASCADE 清空，但照片文件在私有目录不会跟着走，
                    // 不清就全成孤儿。这里先扫干净再导入。
                    photos.deleteAll()
                    // 指向不存在疗程的 parentId 会被置空，避免导入后出现孤儿层级
                    val ids = snap.events.map { it.id }.toSet()
                    snap.events.forEach { ev ->
                        val safe = if (ev.parentId == null || ev.parentId in ids) ev
                        else ev.copy(parentId = null)
                        dao.upsertEvent(safe.toEntity())
                    }
                    snap.records.forEach { dao.insertRecord(it.toEntity()) }
                    // 批量导入绕过了「写入时维护派生列」的路径，必须重算一次
                    dao.recomputeDerived(startOfToday())
                }
                // 合并导入同样绕过了逐条维护，重算兜底
                dao.recomputeDerived(startOfToday())
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
