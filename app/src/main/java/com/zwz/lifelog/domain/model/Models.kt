package com.zwz.lifelog.domain.model

/**
 * 事件类型。
 *
 * 区分它的原因：「没有填期望间隔」与「这件事根本没有周期」是两回事。
 * 前者是让系统从历史里学节奏（status-calculation 的三级回退），
 * 后者是明确告诉系统别催——感冒看完半年没再感冒，不是「逾期」。
 */
enum class EventKind {
    /** 有节奏，走红黄绿状态判定。老数据默认。 */
    PERIODIC,

    /** 按需：只回答「上次是什么时候」，永不判逾期。 */
    ON_DEMAND,

    /** 疗程容器：本身不承载记录，状态由子事件聚合。 */
    COURSE;

    companion object {
        /** JSON / 数据库里的字符串回读，非法值退回 PERIODIC，不让脏数据炸掉启动。 */
        fun of(raw: String?): EventKind =
            entries.firstOrNull { it.name == raw } ?: PERIODIC
    }
}

/**
 * 一个被追踪的事件，例如「理发」「换床单」「看病」。
 *
 * @param targetDays 期望间隔天数；为 null 时表示没有固定节奏，
 *                   由系统根据该事件的历史平均间隔自动判断状态
 *                   （仅对 [EventKind.PERIODIC] 生效）。
 * @param kind       事件类型，决定状态判定走哪条路径。
 * @param parentId   非空表示这是某个疗程下的子事件（如「感冒 2026-09」下的「退烧药」）。
 * @param timesPerDay 每日应完成次数；仅子事件使用。非空时状态按**当日**进度判定，
 *                    与「距上次多久」完全解耦。
 */
data class Event(
    val id: Long = 0L,
    val name: String,
    val emoji: String = "\uD83D\uDCCC",
    val colorArgb: Int = 0xFF2F6FED.toInt(),
    val kind: EventKind = EventKind.PERIODIC,
    val targetDays: Int? = null,
    val timesPerDay: Int? = null,
    val parentId: Long? = null,
    val tag: String? = null,
    val note: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),

    // ---------- 派生列：由 Repository 维护，勿手工写 ----------

    /** 最早一次记录的时间戳；无记录为 null。用于算平均间隔。 */
    val firstTs: Long? = null,

    /** 最近一次记录的时间戳；无记录为 null。 */
    val lastTs: Long? = null,

    /** 累计记录条数。 */
    val recordCount: Int = 0,

    /** 最近一次记录所在日序 `yyyyMMdd`。用于判断「今天」。 */
    val lastRecordDay: Int? = null,

    /** 当日已完成次数；跨天后按 0 读，不写回。 */
    val todayCount: Int = 0
) {

    /** 是否为疗程下的子事件。 */
    val isChild: Boolean get() = parentId != null

    /** 是否为疗程容器。 */
    val isCourse: Boolean get() = kind == EventKind.COURSE

    /** 是否为按需事件（没有周期，系统不催）。 */
    val isOnDemand: Boolean get() = kind == EventKind.ON_DEMAND
}

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

/**
 * 事件的健康状态。
 *
 * `IDLE` = 按需事件：有记录、也有「上次距今多久」，
 * 但系统不回答「该不该做」——因为这件事本来就没有周期。
 */
enum class Freshness { NONE, FRESH, SOON, DUE, IDLE }

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
    /** 最早一次记录的时间戳。按需事件的详情页要显示「首次记录」。 */
    val firstTimestamp: Long? = null,
    /**
     * 距上次记录跨了几个**自然日**：今天=0，昨天=1，前天=2。
     *
     * 只用于界面显示（「X 天前」「昨天」这类文案）。
     * 算新鲜度请看 [ratio] —— 那是按真实流逝时长算的，两者语义不同，别混用。
     */
    val daysAgo: Int?,
    val avgGapMillis: Long?,
    val baselineDays: Int,
    val freshness: Freshness,
    /**
     * 已流逝时长 ÷ 基准间隔，含小数（如 35 小时 / 2 天 = 0.73）。
     * 决定 FRESH / SOON / DUE 与进度条。
     *
     * 频次型事件（[Event.timesPerDay] 非空）这里存的是**当日进度**
     * doneToday / timesPerDay，语义不同，别拿它反推「距上次多久」。
     */
    val ratio: Float,
    val predictedNextMillis: Long?,
    /** 当日已完成次数。仅频次型事件与疗程有意义，其余为 0。 */
    val doneToday: Int = 0,
    /** 疗程的子事件状态，非疗程为空。 */
    val children: List<EventStatusLite> = emptyList()
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
    /** 最早一次记录的时间戳。按需事件详情页显示「首次记录」。 */
    val firstTimestamp: Long? = null,
    /**
     * 距上次记录跨了几个**自然日**：今天=0，昨天=1，前天=2。仅用于显示。
     * 新鲜度判断请用 [ratio]，详见 [EventStatus.daysAgo]。
     */
    val daysAgo: Int?,
    val avgGapMillis: Long?,
    val baselineDays: Int,
    val freshness: Freshness,
    /** 已流逝时长 ÷ 基准间隔，含小数。详见 [EventStatus.ratio]。 */
    val ratio: Float,
    val predictedNextMillis: Long?,
    /** 当日已完成次数。仅频次型事件与疗程有意义，其余为 0。 */
    val doneToday: Int = 0,
    /** 所属疗程名；非空时列表里显示为「疗程 · 子事件」。 */
    val parentName: String? = null,
    /** 疗程的子事件状态，非疗程为空。 */
    val children: List<EventStatusLite> = emptyList()
) {

    /** 今日进度文案，如「今日 1/3」；非频次型事件返回 null。 */
    val todayProgress: String?
        get() = event.timesPerDay
            ?.takeIf { it > 0 }
            ?.let { "今日 $doneToday/$it" }

    /**
     * 转成不含时间线的完整状态。
     *
     * 供小组件这类「只要状态数字、不要记录列表」的场景使用，
     * 避免为了拿一个状态去把这个事件的全部记录加载出来。
     */
    fun toEventStatus(): EventStatus = EventStatus(
        event = event,
        records = emptyList(),
        recordCount = recordCount,
        lastTimestamp = lastTimestamp,
        firstTimestamp = firstTimestamp,
        daysAgo = daysAgo,
        avgGapMillis = avgGapMillis,
        baselineDays = baselineDays,
        freshness = freshness,
        ratio = ratio,
        predictedNextMillis = predictedNextMillis,
        doneToday = doneToday,
        children = children
    )
}

/** 预置模板，供首次启动一键导入。 */
data class Template(
    val name: String,
    val emoji: String,
    val targetDays: Int?,
    val tag: String,
    val kind: EventKind = EventKind.PERIODIC
)

/**
 * 子项建议：疗程里通常要记的那些项。
 *
 * 三种形态用**两个显式字段**区分，而不是让一个数字兼职：
 * - [timesPerDay] 非空 → 频次型（退烧药 3/日）
 * - [periodDays] 非空  → 周期型（复查 每 7 天）
 * - 两者都空           → 一次性（拆线），按需型
 *
 * 早期版本让 `targetDays` 既表示「每日几次」又表示「间隔几天」，
 * 语义全靠上下文猜，极易写反。这里拆开，编译器就能挡住一部分错。
 *
 * @param scene 场景分组，既用于疗程模板取子集，也用于编辑页分区展示
 */
data class ChildSuggestion(
    val name: String,
    val emoji: String,
    val timesPerDay: Int? = null,
    val periodDays: Int? = null,
    val scene: String = "通用"
) {
    val kind: EventKind
        get() = when {
            timesPerDay != null -> EventKind.PERIODIC
            periodDays != null -> EventKind.PERIODIC
            else -> EventKind.ON_DEMAND
        }

    /** chip 上的补充文案。一次性子项没有数字，只显示名字。 */
    val hintText: String
        get() = when {
            timesPerDay != null -> " · $timesPerDay/日"
            periodDays != null -> " · 每 $periodDays 天"
            else -> ""
        }
}

/**
 * 疗程模板：打包好除「名称」外的一切。
 *
 * 与 [Template]（长期事件模板）的区别：
 * - 长期事件模板只填事件本身；疗程模板还带**子项建议**
 * - 长期事件模板按名去重；疗程模板**允许重复创建**
 *   （第二次感冒还要能再建一个「感冒 9月11日」）
 *
 * @param children 该场景常用子项。只作建议呈现，**不会自动创建**——
 *                 每个人的处方不同，擅自替用户决定吃哪种药比让他多点一下更糟
 */
data class CourseTemplate(
    val namePrefix: String,
    val emoji: String,
    val colorArgb: Int,
    val tag: String,
    val hint: String,
    val children: List<ChildSuggestion>,
    /**
     * 自定义模板：场景写死的那几个覆盖不到时（胆结石、运动受伤…）用它。
     *
     * 与普通模板的区别只有两点：
     * - 名字**不预填**，让用户自己起
     * - 没有常用项建议，添加子事件时回退为全部分组，用户自己输入
     */
    val custom: Boolean = false
)

object Templates {
    val ALL = listOf(
        Template("理发", "\u2702\uFE0F", 35, "个人"),
        Template("换床单", "\uD83D\uDECF\uFE0F", 14, "家务"),
        // 感冒这类看病没有周期：不设成 ON_DEMAND 的话，
        // 记满两次就会被历史均值判成「该看病了」
        Template("看病", "\uD83C\uDFE5", null, "健康", EventKind.ON_DEMAND),
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
        // 注：「吃药」不在这里。吃药总是属于某次疗程，作为独立长期事件
        // 是分类错误——它曾导致下次生病无法再从模板添加一个吃药事件。
        // 用药请走 [CHILD_PRESETS]，在疗程详情页添加，可重复。
        Template("中药调理", "\uD83C\uDF75", null, "健康", EventKind.ON_DEMAND),
        Template("针灸", "\uD83E\uDEA8", null, "健康", EventKind.ON_DEMAND),
        Template("贴膏药", "\uD83E\uDDF4", null, "健康", EventKind.ON_DEMAND),
        Template("量血压", "\uD83E\uDED0", 7, "健康"),
        Template("测血糖", "\uD83E\uDE78", 7, "健康"),

        // ---- 夫妻日常 ----
        Template("同房", "\uD83D\uDC97", null, "夫妻", EventKind.ON_DEMAND),
        Template("约会", "\uD83C\uDF77", 30, "夫妻"),
        Template("结婚纪念日", "\uD83D\uDC8D", 365, "夫妻"),
        Template("一起看电影", "\uD83C\uDFAC", 30, "夫妻")
    )

    // ------------------------------------------------------------------
    // 子项建议
    //
    // 按场景分组。分组既用于疗程模板取子集，也用于编辑页分区展示：
    // 宠物疗程不该看到「退烧药」，感冒疗程不该看到「体外驱虫」。
    // ------------------------------------------------------------------

    private val S_FEVER = ChildSuggestion("退烧药", "\uD83D\uDC8A", timesPerDay = 3, scene = "感冒")
    private val S_COUGH = ChildSuggestion("止咳糖浆", "\uD83E\uDDF4", timesPerDay = 3, scene = "感冒")
    private val S_COLD = ChildSuggestion("感冒冲剂", "\uD83C\uDF75", timesPerDay = 3, scene = "感冒")
    private val S_ANTI = ChildSuggestion("消炎药", "\uD83D\uDC8A", timesPerDay = 2, scene = "感冒")
    private val S_NEB = ChildSuggestion("雾化", "\uD83D\uDCA8", timesPerDay = 2, scene = "感冒")

    private val S_DRESSING = ChildSuggestion("换药", "\uD83E\uDDFA", timesPerDay = 2, scene = "术后恢复")
    private val S_STITCH = ChildSuggestion("拆线", "\u2702\uFE0F", scene = "术后恢复")
    private val S_RECHECK = ChildSuggestion("复查", "\uD83C\uDFE5", periodDays = 7, scene = "术后恢复")
    private val S_PAIN = ChildSuggestion("止痛药", "\uD83D\uDC8A", timesPerDay = 3, scene = "术后恢复")

    private val S_INNER = ChildSuggestion("体内驱虫", "\uD83D\uDC8A", periodDays = 30, scene = "宠物")
    private val S_OUTER = ChildSuggestion("体外驱虫", "\uD83E\uDDF4", periodDays = 30, scene = "宠物")
    private val S_VACCINE = ChildSuggestion("疫苗", "\uD83D\uDC89", periodDays = 21, scene = "宠物")

    private val S_HERB = ChildSuggestion("中药", "\uD83C\uDF75", timesPerDay = 2, scene = "调理")
    private val S_ACU = ChildSuggestion("针灸", "\uD83E\uDEA8", periodDays = 7, scene = "调理")

    private val S_VITAMIN = ChildSuggestion("维生素", "\uD83C\uDF7A", timesPerDay = 1, scene = "通用")
    private val S_EYEDROP = ChildSuggestion("眼药水", "\uD83D\uDC41\uFE0F", timesPerDay = 3, scene = "通用")
    private val S_OINTMENT = ChildSuggestion("外用药膏", "\uD83E\uDDF4", timesPerDay = 2, scene = "通用")

    /**
     * 全部子项建议，在疗程详情页「添加子事件」时以快捷 chip 呈现。
     *
     * 与 [ALL] 的区别有两点：
     * 1. **允许重复添加** —— 第二次感冒还要能再加一个「退烧药」
     * 2. 带默认频次/周期，加进来就能用，不必再填一遍
     */
    val CHILD_PRESETS: List<ChildSuggestion> = listOf(
        S_FEVER, S_COUGH, S_COLD, S_ANTI, S_NEB,
        S_DRESSING, S_STITCH, S_RECHECK, S_PAIN,
        S_INNER, S_OUTER, S_VACCINE,
        S_HERB, S_ACU,
        S_VITAMIN, S_EYEDROP, S_OINTMENT
    )

    /** 子项建议按场景分组，编辑页用它分区展示。 */
    fun childPresetsByScene(): List<Pair<String, List<ChildSuggestion>>> =
        CHILD_PRESETS.groupBy { it.scene }.toList()

    /**
     * 按疗程名反查它来自哪个模板，如「感冒 9月11日」→ 感冒模板。
     *
     * 用途：添加子事件时只显示该场景的 3~4 个预设，而不是全部 17 个——
     * 用上下文替代穷举，疗程本来就「知道」自己是什么事。
     *
     * 匹配不到时（手动建的疗程、用户改过名字）返回 null，
     * 调用方回退为展示全部分组，不会因此没法添加。
     */
    fun templateForCourseName(name: String?): CourseTemplate? {
        val n = name?.trim() ?: return null
        // 长前缀优先：「中医调理」要先于「中医」被匹配到
        return COURSE_TEMPLATES
            .filter { n.startsWith(it.namePrefix) }
            .maxByOrNull { it.namePrefix.length }
    }

    /**
     * 疗程模板。
     *
     * 每个模板把「这次是什么事」所需的图标、颜色、分类、类型一次填好，
     * 用户只需确认名字（系统按前缀 + 日期预填）。
     *
     * 颜色取自 [com.zwz.lifelog.ui.edit.EditViewModel.PALETTE]，
     * 保证模板色与手动挑色的取值范围一致。
     *
     **为什么只有四个**：疗程场景要求「有明确终点 + 多项不同节奏 +
     * 关心整体进度」，满足的本来就不多。装修、备考、健身都不满足
     * （见 change 的 proposal），做进来会把疗程页拖成待办清单。
     * 自定义模板不做——加一个场景只是几行数据，成本远低于维护一套
     * 模板管理界面。
     */
    val COURSE_TEMPLATES: List<CourseTemplate> = listOf(
        CourseTemplate(
            namePrefix = "感冒",
            emoji = "\uD83C\uDFE5",
            colorArgb = 0xFFE5484D.toInt(),
            tag = "健康",
            hint = "发烧咳嗽这几天",
            children = listOf(S_FEVER, S_COUGH, S_COLD, S_ANTI)
        ),
        CourseTemplate(
            namePrefix = "术后恢复",
            emoji = "\uD83E\uDE79",
            colorArgb = 0xFF2F6FED.toInt(),
            tag = "健康",
            hint = "换药、拆线、复查",
            children = listOf(S_DRESSING, S_STITCH, S_RECHECK, S_PAIN)
        ),
        CourseTemplate(
            namePrefix = "宠物驱虫",
            emoji = "\uD83D\uDC3E",
            colorArgb = 0xFFE08C00.toInt(),
            tag = "宠物",
            hint = "体内体外分开记，疫苗按针次",
            children = listOf(S_INNER, S_OUTER, S_VACCINE)
        ),
        CourseTemplate(
            namePrefix = "中医调理",
            emoji = "\uD83C\uDF75",
            colorArgb = 0xFF16A34A.toInt(),
            tag = "健康",
            hint = "喝药加针灸的一段周期",
            children = listOf(S_HERB, S_ACU)
        ),
        CourseTemplate(
            namePrefix = "其他",
            emoji = "\uD83D\uDD25",
            colorArgb = 0xFF64748B.toInt(),
            tag = "健康",
            hint = "上面没有的场景，自己起名字",
            children = emptyList(),
            custom = true
        )

    )
}
