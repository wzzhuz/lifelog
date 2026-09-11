package com.zwz.lifelog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 单个事件的聚合统计。
 *
 * 由 SQL 的 COUNT / MIN / MAX 直接得出，
 * 避免把该事件的历史记录逐条读进内存。
 *
 * @param count   记录条数
 * @param firstTs 最早一次记录的时间戳
 * @param lastTs  最近一次记录的时间戳
 */
data class EventStats(
    val eventId: Long,
    val count: Int,
    val firstTs: Long?,
    val lastTs: Long?
)

/**
 * 某事件**当日**已记录条数。
 *
 * 频次型事件（一天吃三次的药）的状态只看今天完成了几次，
 * 「距上次多久」在这里没有意义，所以单列一个聚合。
 */
data class TodayCount(
    val eventId: Long,
    val count: Int
)

@Dao
interface LifeLogDao {

    // ---------- 事件 ----------

    @Query("SELECT * FROM events WHERE isArchived = 0 ORDER BY isPinned DESC, sortOrder ASC, name ASC")
    fun observeActiveEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE isArchived = 1 ORDER BY name ASC")
    fun observeArchivedEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY id ASC")
    fun observeAllEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    fun observeEvent(id: Long): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun eventById(id: Long): EventEntity?

    /**
     * 疗程的**未归档**子事件。
     *
     * 已归档的子事件（比如某种药提前停了）不参与疗程状态聚合，
     * 否则「感冒好了但其中一种药归档了」会让疗程永远显示没吃完。
     */
    @Query("SELECT * FROM events WHERE parentId = :parentId AND isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun activeChildren(parentId: Long): List<EventEntity>

    /** [activeChildren] 的持续订阅版，疗程详情页用。 */
    @Query("SELECT * FROM events WHERE parentId = :parentId AND isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeActiveChildren(parentId: Long): kotlinx.coroutines.flow.Flow<List<EventEntity>>

    /**
     * 全部子事件（含归档），结束/恢复疗程时要整体处理。
     */
    @Query("SELECT * FROM events WHERE parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    suspend fun allChildren(parentId: Long): List<EventEntity>

    /**
     * 疗程结束 / 恢复：父与子一起改。
     *
     * 必须在一个事务里：分批写会让 Flow 发出「改了一半」的中间态，
     * 首页会看到疗程还在、药却没了。
     */
    @androidx.room.Transaction
    suspend fun setArchivedForCourse(parentId: Long, archived: Boolean) {
        setArchived(parentId, archived)
        allChildren(parentId).forEach { setArchived(it.id, archived) }
    }

    /**
     * 删除疗程：子事件与记录一并清掉。
     *
     * 记录由外键 CASCADE 删除；这里要处理的是子事件本身，
     * 否则会留下 parentId 指向已删除疗程的孤儿行。
     */
    @androidx.room.Transaction
    suspend fun deleteEventWithChildren(id: Long) {
        allChildren(id).forEach { deleteEvent(it.id) }
        deleteEvent(id)
    }

    @Query("SELECT * FROM events ORDER BY id ASC")
    suspend fun allEvents(): List<EventEntity>

    /**
     * 归档事件及其记录条数。
     *
     * 用 LEFT JOIN 而非 INNER JOIN：一个记录都没有的事件
     * 也可能是归档状态，不能把它漏掉。
     */
    @Query(
        """
        SELECT e.*, COUNT(r.id) AS recordCount
        FROM events e
        LEFT JOIN records r ON r.eventId = e.id
        WHERE e.isArchived = 1
        GROUP BY e.id
        ORDER BY e.name ASC
        """
    )
    fun observeArchivedWithCount(): kotlinx.coroutines.flow.Flow<List<ArchivedEventRow>>

    /** 归档事件总数，设置页显示入口时用。 */
    @Query("SELECT COUNT(*) FROM events WHERE isArchived = 1")
    fun observeArchivedCount(): kotlinx.coroutines.flow.Flow<Int>

    /** 分页：某事件的记录，按时间倒序取一批。 */
    @Query(
        """
        SELECT * FROM records
        WHERE eventId = :eventId
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun recordsPage(eventId: Long, limit: Int, offset: Int): List<RecordEntity>

    /**
     * 最近若干条记录的流（用于详情页首屏）。
     *
     * 持续订阅，新增记录时自动刷新。
     * 更早的记录通过 [recordsPage] 按需载入，不进这个流——
     * 否则用户每加载一批，首屏就要重组一次。
     */
    @Query(
        """
        SELECT * FROM records
        WHERE eventId = :eventId
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun observeRecordsPage(eventId: Long, limit: Int): kotlinx.coroutines.flow.Flow<List<RecordEntity>>

    @Query("SELECT COUNT(*) FROM events")
    fun observeEventCount(): Flow<Int>

    @Upsert
    suspend fun upsertEvent(e: EventEntity): Long

    @Update
    suspend fun updateEvent(e: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("UPDATE events SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: Long)

    @Query("UPDATE events SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * 当日各事件的记录条数。
     *
     * 与 [observeAllStats] 同思路：走 SQL 聚合，不把记录读进内存。
     * `dayStart` 由调用方按设备时区算出（当天 00:00 的时间戳）。
     */
    @Query(
        """
        SELECT eventId, COUNT(*) AS `count`
        FROM records
        WHERE timestamp >= :dayStart
        GROUP BY eventId
        """
    )
    fun observeTodayCounts(dayStart: Long): kotlinx.coroutines.flow.Flow<List<TodayCount>>

    /** [observeTodayCounts] 的一次性版本，供小组件等不需要持续订阅的场景使用。 */
    @Query(
        """
        SELECT eventId, COUNT(*) AS `count`
        FROM records
        WHERE timestamp >= :dayStart
        GROUP BY eventId
        """
    )
    suspend fun todayCountsOnce(dayStart: Long): List<TodayCount>

    /**
     * 批量更新手动排序。
     *
     * 拖拽结束后一次性写回，避免每移动一格就写一次库。
     * 参数是有序的事件 id 列表，下标即新的 sortOrder。
     */
    @Query("UPDATE events SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    /**
     * 整批写入排序。
     *
     * 必须包在**一个事务**里：逐条 UPDATE 会让 Flow 发射 N 次，
     * 每次都带着「改了一半」的中间状态，UI 端的同步逻辑
     * 会被这些中间态反复打断，最终顺序可能与拖拽结果不一致。
     * 一个事务只发射一次，拿到就是最终状态。
     */
    @androidx.room.Transaction
    suspend fun updateSortOrders(ids: List<Long>) {
        ids.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    @Query("SELECT * FROM events WHERE isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun activeEventsSorted(): List<EventEntity>

    // ---------- 派生列 ----------

    /**
     * 新增记录后推进派生列。
     *
     * 由 Repository 在**插入记录的同一个事务**里调用，
     * 因此这里只做数值推进，不再查库（除了一次 eventById）。
     */
    @Query(
        """
        UPDATE events
        SET firstTs        = MIN(IFNULL(firstTs, :ts), :ts),
            lastTs         = :lastTs,
            recordCount    = recordCount + 1,
            lastRecordDay  = :day,
            todayCount     = CASE WHEN lastRecordDay = :day THEN todayCount + 1 ELSE 1 END
        WHERE id = :eventId
        """
    )
    suspend fun bumpDerived(eventId: Long, ts: Long, lastTs: Long, day: Int)

    /** 删除记录后回退派生列。count 与 day 由调用方算好。 */
    @Query(
        """
        UPDATE events
        SET recordCount   = :count,
            lastTs        = :lastTs,
            lastRecordDay = :day,
            todayCount    = :todayCount
        WHERE id = :eventId
        """
    )
    suspend fun setDerived(eventId: Long, count: Int, lastTs: Long?, day: Int?, todayCount: Int)

    /**
     * 全量重算派生列（幂等）。
     *
     * 一次 UPDATE 完成，不做增量：写入路径漏更新、迁移后、
     * 或导入之后都靠它恢复一致。
     *
     * lastRecordDay 用 `strftime('%Y%m%d', lastTs/1000, 'localtime')`
     * 由 SQLite 按设备时区的本地时间算日序——这与 Kotlin 侧
     * [dayKeyOf] 的口径一致（都取本地日历日）。
     */
    @Query(
        """
        UPDATE events
        SET firstTs = (
                SELECT MIN(timestamp) FROM records WHERE records.eventId = events.id
            ),
            lastTs = (
                SELECT MAX(timestamp) FROM records WHERE records.eventId = events.id
            ),
            recordCount = (
                SELECT COUNT(*) FROM records WHERE records.eventId = events.id
            ),
            lastRecordDay = (
                SELECT CAST(strftime('%Y%m%d', MAX(timestamp) / 1000, 'localtime') AS INTEGER)
                FROM records WHERE records.eventId = events.id
            ),
            todayCount = (
                SELECT COUNT(*) FROM records
                WHERE records.eventId = events.id
                  AND timestamp >= :dayStart
            )
        """
    )
    suspend fun recomputeDerived(dayStart: Long)

    /**
     * 重算**单个**事件的派生列，用于删除记录后的回退。
     *
     * 不靠减法推 lastTs（容易算错），而是直接取 MAX。
     */
    @Query(
        """
        SELECT :eventId       AS eventId,
               COUNT(*)       AS `count`,
               MIN(timestamp) AS firstTs,
               MAX(timestamp) AS lastTs
        FROM records
        WHERE eventId = :eventId
        """
    )
    suspend fun statsOfEvent(eventId: Long): EventStats

    /** 当日该事件的记录条数。 */
    @Query("SELECT COUNT(*) FROM records WHERE eventId = :eventId AND timestamp >= :dayStart")
    suspend fun countSince(eventId: Long, dayStart: Long): Int

    /** 只取照片文件名：删事件时不再把整个事件的记录搬进内存。 */
    @Query("SELECT photoName FROM records WHERE eventId = :eventId AND photoName IS NOT NULL")
    suspend fun photoNamesOf(eventId: Long): List<String>

    // ---------- 记录 ----------

    @Query("SELECT * FROM records WHERE eventId = :eventId ORDER BY timestamp DESC")
    fun observeRecordsOf(eventId: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE eventId = :eventId ORDER BY timestamp DESC")
    suspend fun recordsOf(eventId: Long): List<RecordEntity>

    @Query("SELECT * FROM records ORDER BY timestamp DESC")
    suspend fun allRecords(): List<RecordEntity>

    /**
     * 全局时间线分页。
     *
     * 实测：200 万条时首页 0.03ms / 第 100 页 0.12ms，
     * 而全表读要 2.5 秒。时间线因此改为按需翻页，
     * 不再一次性把全部记录读进内存。
     */
    @Query("SELECT * FROM records ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun recordsGlobalPage(limit: Int, offset: Int): List<RecordEntity>

    @Query("SELECT * FROM records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentRecords(limit: Int): List<RecordEntity>

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun recordById(id: Long): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(r: RecordEntity): Long

    @Update
    suspend fun updateRecord(r: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM records WHERE eventId = :eventId")
    suspend fun deleteRecordsOf(eventId: Long)

    /**
     * 全部事件的聚合统计。
     *
     * 这是首页列表不加载记录的关键：一次 GROUP BY 拿到所有事件
     * 的条数与首尾时间，供 [com.zwz.lifelog.domain.usecase.StatusCalculator.computeLite]
     * 直接算出状态。
     */
    @Query(
        """
        SELECT eventId,
               COUNT(*)       AS `count`,
               MIN(timestamp) AS firstTs,
               MAX(timestamp) AS lastTs
        FROM records
        GROUP BY eventId
        """
    )
    fun observeAllStats(): Flow<List<EventStats>>

    /** [observeAllStats] 的一次性版本，供小组件等不需要持续订阅的场景使用。 */
    @Query(
        """
        SELECT eventId,
               COUNT(*)       AS `count`,
               MIN(timestamp) AS firstTs,
               MAX(timestamp) AS lastTs
        FROM records
        GROUP BY eventId
        """
    )
    suspend fun allStatsOnce(): List<EventStats>

    @Query(
        """
        SELECT :eventId       AS eventId,
               COUNT(*)       AS `count`,
               MIN(timestamp) AS firstTs,
               MAX(timestamp) AS lastTs
        FROM records
        WHERE eventId = :eventId
        """
    )
    suspend fun statsOf(eventId: Long): EventStats?

    /**
     * 单个事件的聚合统计（持续订阅）。
     *
     * 详情页分页后，内存里只有最近 20 条记录，
     * 但「累计次数 / 平均间隔 / 预测下次」需要**全量**数据。
     * 这里用 COUNT / MIN / MAX 一次算完，不加载记录行。
     */
    @Query(
        """
        SELECT :eventId AS eventId,
               COUNT(r.id) AS count,
               MIN(r.timestamp) AS firstTs,
               MAX(r.timestamp) AS lastTs
        FROM events e
        LEFT JOIN records r ON r.eventId = e.id
        WHERE e.id = :eventId
        GROUP BY e.id
        """
    )
    fun observeStatsOf(eventId: Long): kotlinx.coroutines.flow.Flow<EventStats?>

    // ---------- 搜索 ----------

    /**
     * 按备注内容搜索命中的事件 id。
     *
     * 走 SQL 过滤而不是把记录全读进内存再遍历：
     * 数据库层过滤完只返回 id 列表，不构造 Record 对象。
     */
    @Query("SELECT DISTINCT eventId FROM records WHERE note LIKE '%' || :kw || '%'")
    suspend fun searchEventIdsByNote(kw: String): List<Long>

    /** 按事件名或标签搜索（事件表很小，全表扫描也很快）。 */
    @Query("SELECT id FROM events WHERE name LIKE '%' || :kw || '%' OR tag LIKE '%' || :kw || '%'")
    suspend fun searchEventIdsByNameOrTag(kw: String): List<Long>

    /** 指定时间段内的记录，供年度回顾使用。 */
    @Query("SELECT * FROM records WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun recordsBetween(from: Long, to: Long): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM records")
    fun observeRecordCount(): Flow<Int>

    /** 记录总数（一次性），供时间线分页判断是否还有更早的。 */
    @Query("SELECT COUNT(*) FROM records")
    suspend fun recordCountOnce(): Int
}
