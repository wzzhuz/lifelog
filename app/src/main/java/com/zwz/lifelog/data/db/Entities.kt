package com.zwz.lifelog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 事件表。
 *
 * 索引说明：
 * - name：模板导入时需要按名字查重
 * - tag：筛选页按标签过滤
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["name"]),
        Index(value = ["tag"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String = "\uD83D\uDCCC",
    val colorArgb: Int = 0xFF2F6FED.toInt(),
    val targetDays: Int? = null,
    val tag: String? = null,
    val note: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 记录表。
 *
 * 索引说明：
 * - (eventId, timestamp)：详情页按事件查时间线，最频繁的查询
 * - timestamp：全局时间线与年度回顾按时间排序
 */
@Entity(
    tableName = "records",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventId: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val photoName: String? = null,
    val loggedAt: Long = System.currentTimeMillis()
)

// ---------- 领域模型 ↔ 实体 互转 ----------

fun EventEntity.toDomain(): com.zwz.lifelog.domain.model.Event =
    com.zwz.lifelog.domain.model.Event(
        id = id,
        name = name,
        emoji = emoji,
        colorArgb = colorArgb,
        targetDays = targetDays,
        tag = tag,
        note = note,
        isPinned = isPinned,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = createdAt
    )

fun com.zwz.lifelog.domain.model.Event.toEntity(): EventEntity =
    EventEntity(
        id = id,
        name = name,
        emoji = emoji,
        colorArgb = colorArgb,
        targetDays = targetDays,
        tag = tag,
        note = note,
        isPinned = isPinned,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = createdAt
    )

fun RecordEntity.toDomain(): com.zwz.lifelog.domain.model.Record =
    com.zwz.lifelog.domain.model.Record(
        id = id,
        eventId = eventId,
        timestamp = timestamp,
        note = note,
        photoName = photoName,
        loggedAt = loggedAt
    )

fun com.zwz.lifelog.domain.model.Record.toEntity(): RecordEntity =
    RecordEntity(
        id = id,
        eventId = eventId,
        timestamp = timestamp,
        note = note,
        photoName = photoName,
        loggedAt = loggedAt
    )

/**
 * 归档事件 + 其记录条数。
 *
 * 单独定义而非复用 EventEntity：设置页的归档列表要显示
 * 「这个事件有多少条记录」，而 EventEntity 没有这个字段，
 * 直接在 UI 层二次查询会导致 N+1。
 */
data class ArchivedEventRow(
    val id: Long,
    val name: String,
    val emoji: String,
    val colorArgb: Int,
    val targetDays: Int?,
    val tag: String?,
    val note: String?,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val recordCount: Int
)
