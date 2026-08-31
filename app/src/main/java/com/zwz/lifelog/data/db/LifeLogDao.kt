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
