# 技术设计：疗程模板与子事件场景扩展

change-id：`add-course-templates-and-more-scenarios`

## 0. 现状

### 0.1 新建疗程的代码路径

`EditEventScreen` 对**所有**新建事件一视同仁，按顺序要求填写：

| 区块 | 字段 | 疗程场景是否可预测 |
|---|---|---|
| 名称 | `name` | ❌ 唯一需要人输入的 |
| 图标 | `emoji` | ✅ 由场景定 |
| 颜色 | `colorArgb` | ✅ 由场景定 |
| 类型 | `kind = COURSE` | ✅ 选了疗程模板就是它 |
| 分类 | `tag` | ✅ 由场景定 |
| 期望间隔 | 疗程隐藏 | — |

结论：5 个决策里 4 个可预测，当前却全要手动。

### 0.2 子事件的类型支持（已确认够用）

子事件就是 `parentId != null` 的 `Event`，`kind` 可为
`PERIODIC` / `ON_DEMAND` / `COURSE` 中任意一种——**数据模型已支持
混合类型的子事件**，本次不需要改 schema。

（顺便：子事件的 `kind` 选择器不该出现 `COURSE`，否则能造出三层层级。
本次在编辑页对 `parentId != null` 的事件隐藏「疗程」选项。）

### 0.3 已发现的一个缺口

`StatusCalculator.aggregateCourseLite`：

```kotlin
val target = children.sumOf { it.event.timesPerDay?.coerceAtLeast(0) ?: 0 }
```

分母只累加**频次型**子事件的 `timesPerDay`。因此：

- 全是周期型子事件的疗程（宠物疫苗三针）→ `target = 0` → ratio 0，
  `courseProgress()` 返回 null → **首页卡片完全没有进度信息**
- 但 `freshness` 取子事件里最紧急的一档，这个**是正常工作的**

所以要补的只是「没有频次型子事件时的进度回退」，见 §5。

## 1. CourseTemplate 数据模型

```kotlin
/**
 * 疗程模板：打包好除「名称」外的一切。
 *
 * 与 [Template]（长期事件模板）的区别：
 * - 长期事件模板只填事件本身，疗程模板还带**子项建议**
 * - 长期事件模板按名去重，疗程模板**允许重复创建**
 *   （第二次感冒还要能再建一个「感冒 9月11日」）
 */
data class CourseTemplate(
    /** 名称前缀，如「感冒」。最终名称 = 前缀 + 日期后缀。 */
    val namePrefix: String,
    val emoji: String,
    val colorArgb: Int,
    val tag: String,
    /** 该场景常用子项名，引用 [Templates.CHILD_PRESETS] 里的名字。 */
    val childNames: List<String>,
    /** 一句话说明，显示在 chip 下方。 */
    val hint: String
)
```

放在 `Templates` 里，与 `ALL` / `CHILD_PRESETS` 并列：

```kotlin
object Templates {
    val ALL: List<Template>              // 长期事件，按名去重
    val CHILD_PRESETS: List<Template>    // 子项预设，允许重复
    val COURSE_TEMPLATES: List<CourseTemplate>   // 疗程模板，允许重复
}
```

内置四个：

| 前缀 | 图标 | 分类 | 子项建议 |
|---|---|---|---|
| 感冒 | 🏥 | 健康 | 退烧药、止咳糖浆、感冒冲剂 |
| 术后恢复 | 🩹 | 健康 | 换药、拆线、复查 |
| 宠物驱虫 | 🐾 | 宠物 | 体内驱虫、体外驱虫、疫苗 |
| 中医调理 | 🍵 | 健康 | 中药、针灸 |

为什么只做四个而不是让用户自定义：场景有限，自定义模板要引入
一整套模板管理界面（增删改排序），收益远小于成本。

## 2. 创建流程改造

### 2.1 新建页加「快速开始」

`EditEventScreen` 在**新建顶层事件**（`id == 0 && parentId == null`）时，
顶部显示「快速开始」区：

```
快速开始
[🏥 感冒] [🩹 术后恢复] [🐾 宠物驱虫] [🍵 中医调理]
```

点击 → `vm.applyCourseTemplate(t)`：

```kotlin
fun applyCourseTemplate(t: CourseTemplate) {
    _draft.value = _draft.value.copy(
        name = suggestCourseName(t.namePrefix),   // 「感冒 9月11日」
        emoji = t.emoji,
        colorArgb = t.colorArgb,
        tag = t.tag,
        kind = EventKind.COURSE
    )
    _advancedExpanded.value = false   // 高级区收起
}
```

### 2.2 高级设置折叠

已由模板填好的四项（图标 / 颜色 / 类型 / 分类）收进
「更多设置（已自动填好）」可展开区，默认收起。

**为什么不是直接隐藏**：用户可能想改图标或颜色，
隐藏了就没法改；折叠既保证默认零决策，又留了口子。

展开状态由 ViewModel 的 `_advancedExpanded` 控制，
未选模板时默认为**展开**（保持现有普通事件的创建体验不变）。

### 2.3 保存后直接进疗程页

选了疗程模板时，保存后不回首页，而是`onSaved(eventId)` 带 id
跳到疗程详情页——那里正好接着加药，路径连贯。

普通事件保持现状（回首页）。

## 3. 名称自动生成与去重

```kotlin
/**
 * 「前缀 + 今天」，如「感冒 9月11日」。
 *
 * 日期用「M月d日」而不是完整年份：疗程跨度通常只有几周，
 * 年份是冗余信息，而卡片宽度有限。
 */
private fun suggestCourseName(prefix: String): String {
    val today = SimpleDateFormat("M月d日", Locale.CHINA).format(Date())
    return "$prefix $today"
}
```

**重名处理**：同一天建两个「感冒」时会撞名。追加序号：

```kotlin
// 已存在同名 → 「感冒 9月11日 · 2」
suspend fun uniqueCourseName(base: String): String
```

查 `dao.allEvents()` 里是否已存在该名（**含已归档**——
已归档的同名疗程也算撞名，虽然不影响使用，但归档页会出现两个一模一样的名字）。

## 4. 「再来一次」：从上次复刻

```kotlin
/**
 * 复制一个疗程及其全部子事件，生成新的同名（新日期）疗程。
 *
 * 一个事务内完成，避免中间态被 Flow 发出去。
 */
suspend fun duplicateCourse(sourceId: Long, newName: String): Long
```

复制内容：

| 字段 | 疗程 | 子事件 |
|---|---|---|
| 名称 | 新生成的（前缀+今天） | 原样保留 |
| emoji / color / tag / kind | 原样 | 原样 |
| timesPerDay | — | **原样**（这是复刻的主要价值） |
| 记录 | ❌ 不复制 | ❌ 不复制 |

**刻意不复制记录**：新疗程是「这一次」，上次的吃药记录属于「那一次」。
复制过来会让新疗程一建成就显示"已记 8 次"，完全失真。

入口：新建页「快速开始」下方，列最近 3 个疗程
（`ORDER BY createdAt DESC LIMIT 3`），格式：

```
再来一次
[🏥 感冒 8月3日 · 3 项]
```

子项数取 `activeChildren + allChildren` 的并集（含已归档的，
因为疗程归档时子事件一起归档了）。

## 5. 混合类型子事件的进度回退

`courseProgress()` 在 `target == 0` 时返回 null。补一个回退：

```kotlin
fun courseProgress(parent: EventStatusLite): String? {
    val target = parent.children.sumOf { it.event.timesPerDay ?: 0 }
    if (target > 0) return "今日 ${parent.doneToday}/$target"

    // 没有频次型子事件（如「宠物疫苗」三针都是周期型）：
    // 退而显示最紧急的那一项，否则卡片完全没有状态信息
    val urgent = parent.children
        .filter { it.freshness == Freshness.DUE || it.freshness == Freshness.SOON }
        .minByOrNull { priority(it.freshness) }   // 注意 priority 越大越紧急
    return urgent?.let { "${it.event.name} · 该做了" }
}
```

> `priority()` 当前是 `private`，`DUE` 最大。这里要用降序取最大，
> 实现时注意别写反（已有实现里用的是 `maxByOrNull { priority(...) }`）。

仅限**首页卡片副标题**使用，详情页的「子事件 N 项」不受影响。

## 6. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 模板选错场景，用户不知道能改 | 图标颜色不合意 | 高级区折叠但可展开，不隐藏 |
| 复刻时子事件指向旧疗程的引用残留 | 数据错乱 | 复刻在一个事务内完成，新子事件写新的 parentId |
| 名称撞名导致两个同名疗程 | 归档页难分辨 | `uniqueCourseName` 追加序号 |
| 子事件被选成 COURSE 造成三层 | 层级失控 | 编辑页对子事件隐藏「疗程」选项 |
| 模板数据写死，用户想加场景 | 不够灵活 | 明确不做自定义；加场景只需改一行数据，成本极低 |

## 7. 验证方式

- 单测：`suggestCourseName` 格式、`uniqueCourseName` 撞名、
  `duplicateCourse` 不复制记录、`courseProgress` 无频次子事件时的回退
- 单测：疗程模板填好后 `kind == COURSE` 且 `targetDays == null`
- 手动验证：点「感冒」→ 确认名称已预填、四项已填好 → 保存 →
  直接进疗程页 → 点两个药 → 回首页确认「今日 0/5」与缩进层级
- 手动验证：「再来一次」→ 确认新疗程带旧子事件与频次，但记录数为 0
- 改动涉及选型/功能增删时运行 `scripts/check_docs_consistency.py`；
  涉及 Kotlin 字符串时运行 `scripts/check_kotlin_strings.py`
