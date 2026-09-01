package com.zwz.lifelog.domain.model

/**
 * 一个被追踪的事件，例如「理发」「换床单」「看病」。
 *
 * @param targetDays 期望间隔天数；为 null 时表示没有固定节奏，
 *                   由系统根据该事件的历史平均间隔自动判断状态。
 */
data class Event(
    val id: Long = 0L,
    val name: String,
    val emoji: String = "\uD83D\uDCCC",
    val colorArgb: Int = 0xFF2F6FED.toInt(),
    val targetDays: Int? = null,
    val tag: String? = null,
    val note: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    /** 分组模式下的组内顺序，与 sortOrder 互不干扰。 */
    val sortInGroup: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 一次记录。
 *
 * @param timestamp 事件发生时间，可以倒填（例如昨天理的发今天才想起记录）
 * @param loggedAt  实际录入手机的时间，与 timestamp 区分，便于日后排查误操作
 */
data class Record(
    val id: Long = 0L,
    val eventId: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val photoName: String? = null,
    val loggedAt: Long = System.currentTimeMillis()
)

/** 事件的健康状态。 */
enum class Freshness { NONE, FRESH, SOON, DUE }

/**
 * 事件 + 其派生状态，UI 层直接消费这个对象。
 *
 * 注意：包含完整的 records 列表，**仅供详情页使用**。
 * 首页列表请用 [EventStatusLite]——它不携带记录列表，
 * 只靠 SQL 聚合统计得出，避免把上万条记录全部加载进内存。
 */
data class EventStatus(
    val event: Event,
    val records: List<Record>,
    /**
     * 该事件的**全部**记录条数。
     *
     * 分页后 [records] 只含最近若干条，这个字段才是真实总数——
     * 由 SQL COUNT 得出，不依赖已加载的记录。
     */
    val recordCount: Int = records.size,
    val lastTimestamp: Long?,
    val daysSince: Int?,
    val avgGapMillis: Long?,
    val baselineDays: Int,
    val freshness: Freshness,
    val ratio: Float,
    val predictedNextMillis: Long?
)

/**
 * 首页列表专用的轻量状态。
 *
 * 与 [EventStatus] 的区别：**不携带 records 列表**。
 * 字段由 SQL 聚合查询直接算出（COUNT / MIN / MAX），
 * 无需把该事件的历史记录读进内存，因此列表渲染开销
 * 与总记录数无关，只与事件数有关（通常几十个）。
 *
 * @param recordCount 该事件的历史记录条数
 */
data class EventStatusLite(
    val event: Event,
    val recordCount: Int,
    val lastTimestamp: Long?,
    val daysSince: Int?,
    val avgGapMillis: Long?,
    val baselineDays: Int,
    val freshness: Freshness,
    val ratio: Float,
    val predictedNextMillis: Long?
)

/** 预置模板，供首次启动一键导入。 */
data class Template(
    val name: String,
    val emoji: String,
    val targetDays: Int?,
    val tag: String
)

object Templates {
    val ALL = listOf(
        Template("理发", "\u2702\uFE0F", 35, "个人"),
        Template("换床单", "\uD83D\uDECF\uFE0F", 14, "家务"),
        Template("看病", "\uD83C\uDFE5", null, "健康"),
        Template("洗牙", "\uD83E\uDDB7", 365, "健康"),
        Template("体检", "\uD83D\uDC8A", 365, "健康"),
        Template("换牙刷", "\uD83E\uDDA5", 90, "个人"),
        Template("换滤芯", "\uD83E\uDDF4", 90, "家务"),
        Template("汽车保养", "\uD83D\uDE97", 180, "汽车"),
        Template("洗车", "\uD83D\uDEA8", 30, "汽车"),
        Template("宠物驱虫", "\uD83D\uDC3E", 30, "宠物"),
        Template("宠物洗澡", "\uD83D\uDEB4", 14, "宠物"),
        Template("浇花", "\uD83E\uDDEB", 7, "家务"),
        Template("深度清洁", "\uD83E\uDDF9", 90, "家务"),
        Template("洗空调滤网", "\u2744\uFE0F", 180, "家务"),
        Template("换隐形眼镜", "\uD83D\uDC41\uFE0F", 30, "个人"),
        Template("剪指甲", "\uD83D\uDC85", 14, "个人"),
        Template("备份手机", "\uD83D\uDCF1", 30, "数码"),
        Template("改密码", "\uD83D\uDD11", 180, "数码"),
        Template("检查订阅扣费", "\uD83D\uDCB3", 90, "财务"),
        Template("看望父母", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", 30, "人际"),
        Template("换毛巾", "\uD83E\uDDFC", 30, "家务"),
        Template("清理冰箱", "\uD83E\uDDCA", 90, "家务"),
        Template("换机油", "\uD83D\uDEE2\uFE0F", 180, "汽车"),
        Template("整理照片", "\uD83D\uDCF7", 90, "数码"),

        // ---- 健康调理 ----
        Template("吃药", "\uD83D\uDC8A", 1, "健康"),
        Template("中药调理", "\uD83C\uDF75", null, "健康"),
        Template("针灸", "\uD83E\uDEA8", null, "健康"),
        Template("贴膏药", "\uD83E\uDDF4", null, "健康"),
        Template("量血压", "\uD83E\uDED0", 7, "健康"),
        Template("测血糖", "\uD83E\uDE78", 7, "健康"),

        // ---- 夫妻日常 ----
        Template("同房", "\uD83D\uDC97", null, "夫妻"),
        Template("约会", "\uD83C\uDF77", 30, "夫妻"),
        Template("结婚纪念日", "\uD83D\uDC8D", 365, "夫妻"),
        Template("一起看电影", "\uD83C\uDFAC", 30, "夫妻")
    )
}
