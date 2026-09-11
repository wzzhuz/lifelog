package com.zwz.lifelog.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 事件表。
 *
 * 索引说明：
 * - name：模板导入时需要按名字查重
 * - tag：筛选页按标签过滤
 * - parentId：疗程详情页查子事件
 *
 * **派生列**（`lastTs` / `recordCount` / `lastRecordDay` / `todayCount`）：
 * 由 Repository 在写入记录时于同一事务内维护，
 * 首页因此不必再对 records 表做 GROUP BY。
 * 详见 `LifeLogRepository.recomputeDerived`。
 *
 * ⚠️ 任何绕过 Repository 直接写 records 的路径都会破坏一致性。
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["name"]),
        Index(value = ["tag"]),
        Index(value = ["parentId"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String = "\uD83D\uDCCC",
    val colorArgb: Int = 0xFF2F6FED.toInt(),
    /**
     * 事件类型，存枚举名。
     *
     * 存 String 而不是加 TypeConverter：少一个注解处理器路径，
     * 也少一处 schema 校验不一致的可能。回读时由
     * `com.zwz.lifelog.domain.model.EventKind.of` 兜底。
     *
     * 默认值必须是**字面量**：Room 的注解处理器要解析构造参数的默认值
     * 写进建表语句，`EventKind.PERIODIC.name` 这种运行期取值它解析不了。
     */
    @ColumnInfo(defaultValue = "'PERIODIC'")
    val kind: String = "PERIODIC",
    val targetDays: Int? = null,
    /** 每日应完成次数；仅子事件使用。 */
    val timesPerDay: Int? = null,
    /** 所属疗程（父事件）id。 */
    val parentId: Long? = null,
    val tag: String? = null,
    val note: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * 分组模式下的组内顺序。
     *
     * 与 sortOrder 分开存储的原因：两种模式的排序语义不同——
     * 列表模式是全局顺序，分组模式是「先按标签分组、组内再排」。
     * 共用一个字段时，在分组里拖一下会把全局顺序重写成
     * 按标签排列的完整顺序，切回列表模式就全乱了。
     */
    /**
     * 已废弃：随分组模式一并停用，代码里不再读写。
     *
     * 列本身保留——SQLite 删列要重建整表，为一个废列单独做一次迁移
     * 不划算，等下次有真正的 schema 变更时再一起处理。
     */
    @Deprecated("分组模式已删除，仅保留列")
    val sortInGroup: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),

    // ---------- 派生列：由 Repository 维护，勿手工写 ----------

    /** 最早一次记录的时间戳；无记录为 null。用于算平均间隔。 */
    val firstTs: Long? = null,

    /** 最近一次记录的时间戳；无记录为 null。 */
    val lastTs: Long? = null,

    /** 累计记录条数。 */
    val recordCount: Int = 0,

    /**
     * 最近一次记录所在的「日序」，格式 `yyyyMMdd`（如 20260911）。
     *
     * 用整数日序而非时间戳，是为了让「是不是今天」只需一次整数比较，
     * 且不受具体时刻影响。
     */
    val lastRecordDay: Int? = null,

    /** 当日已完成次数。读时若发现 [lastRecordDay] 不是今天，一律按 0 处理。 */
    val todayCount: Int = 0
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
        kind = com.zwz.lifelog.domain.model.EventKind.of(kind),
        targetDays = targetDays,
        timesPerDay = timesPerDay,
        parentId = parentId,
        tag = tag,
        note = note,
        isPinned = isPinned,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        firstTs = firstTs,
        lastTs = lastTs,
        recordCount = recordCount,
        lastRecordDay = lastRecordDay,
        todayCount = todayCount
    )

fun com.zwz.lifelog.domain.model.Event.toEntity(): EventEntity =
    EventEntity(
        id = id,
        name = name,
        emoji = emoji,
        colorArgb = colorArgb,
        kind = kind.name,
        targetDays = targetDays,
        timesPerDay = timesPerDay,
        parentId = parentId,
        tag = tag,
        note = note,
        isPinned = isPinned,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        firstTs = firstTs,
        lastTs = lastTs,
        recordCount = recordCount,
        lastRecordDay = lastRecordDay,
        todayCount = todayCount
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
    val kind: String,
    val targetDays: Int?,
    val timesPerDay: Int?,
    val parentId: Long?,
    val tag: String?,
    val note: String?,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val recordCount: Int
)

/** 归档行是不是疗程——疗程恢复时要连同子事件一起带回来。 */
fun ArchivedEventRow.isCourseRow(): Boolean =
    com.zwz.lifelog.domain.model.EventKind.of(kind) ==
        com.zwz.lifelog.domain.model.EventKind.COURSE
