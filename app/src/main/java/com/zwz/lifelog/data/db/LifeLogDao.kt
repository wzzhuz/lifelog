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

@Dao
interface LifeLogDao {

    // ---------- 事件 ----------

    @Query("SELECT * FROM events WHERE isArchived = 0 ORDER BY isPinned DESC, sortOrder ASC, name ASC")
    fun observeActiveEvents(): Flow<List<EventEntity>>

    /**
     * 分组模式的顺序：先按标签归组，组内按 sortInGroup 排。
     *
     * 与 [observeActiveEvents] 分开，两个字段各管各的模式，
     * 互不污染。NULL 标签排最后（SQLite 默认 NULL 最小，
     * 这里用 CASE 显式调整，避免「未分类」凭空跑到最前）。
     */
    @Query(
        """
        SELECT * FROM events
        WHERE isArchived = 0
        ORDER BY CASE WHEN tag IS NULL OR tag = '' THEN 1 ELSE 0 END,
                 tag ASC,
                 isPinned DESC,
                 sortInGroup ASC,
                 name ASC
        """
    )
    fun observeActiveEventsGrouped(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE isArchived = 1 ORDER BY name ASC")
    fun observeArchivedEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY id ASC")
    fun observeAllEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    fun observeEvent(id: Long): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun eventById(id: Long): EventEntity?

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

    @Query("UPDATE events SET sortInGroup = :order WHERE id = :id")
    suspend fun updateSortInGroup(id: Long, order: Int)

    /** 分组模式排序：同样整批事务写入。 */
    @androidx.room.Transaction
    suspend fun updateSortInGroups(ids: List<Long>) {
        ids.forEachIndexed { index, id -> updateSortInGroup(id, index) }
    }

    @Query("SELECT * FROM events WHERE isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun activeEventsSorted(): List<EventEntity>

    // ---------- 记录 ----------

    @Query("SELECT * FROM records WHERE eventId = :eventId ORDER BY timestamp DESC")
    fun observeRecordsOf(eventId: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE eventId = :eventId ORDER BY timestamp DESC")
    suspend fun recordsOf(eventId: Long): List<RecordEntity>

    @Query("SELECT * FROM records ORDER BY timestamp DESC")
    suspend fun allRecords(): List<RecordEntity>

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
}
