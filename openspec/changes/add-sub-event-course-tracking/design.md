# 技术设计：子事件与疗程

对应 change：`add-sub-event-course-tracking`

---

## 1. 数据模型

### 1.1 `events` 表新增三个字段

```kotlin
// EventEntity.kt / Event.kt
val parentId: Long? = null        // 非空 = 子事件，指向疗程（父事件）
val timesPerDay: Int? = null      // 每日应完成次数；null = 不按频次判定
val kind: EventKind = EventKind.PERIODIC
```

```kotlin
enum class EventKind { PERIODIC, ON_DEMAND, COURSE }
```

| kind | 含义 | 谁来用 |
|---|---|---|
| `PERIODIC` | 有节奏，走现有红黄绿 | 理发、换床单、量血压；老数据默认 |
| `ON_DEMAND` | 按需，永不判逾期 | 看病（感冒）、针灸、同房 |
| `COURSE` | 疗程容器，本身不承载记录 | 「感冒 2026-09」 |

**为什么用 kind 而不是「`targetDays == null` 即按需」**：
`targetDays` 留空目前有明确用途——「记满两次后让系统自动学会节奏」
（status-calculation 的三级回退）。把它重新解释成「无周期」，
等于砍掉「看病记两次后系统自动估算」这条既有能力，是 regressions。
多一个字段成本远低于语义歧义。

**为什么父事件也算一个 Event（`COURSE`）而不是新表**：
复用现有的一切——增删改、归档与恢复、钉选、排序、导出、小组件选事件，
全都不用重写；新表意味着这些入口都要为它单独开一条分支。
代价是「疗程」出现在事件列表时要能被识别（kind 判断），可控。

**为什么只做两层**：疗程是唯一需要「整体结束」的层级。
再往上的「看病」只是标签语义（tag=健康），不构成生命周期。

### 1.2 迁移（2 → 3）

```kotlin
val MIGRATION_2_3 = Migration(2, 3) {
    it.execSQL("ALTER TABLE events ADD COLUMN parentId INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN timesPerDay INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN kind TEXT NOT NULL DEFAULT 'PERIODIC'")
}
```

Room 侧用 `@ColumnInfo(defaultValue = "PERIODIC")` 让 schema 校验与迁移 SQL 一致。
`parentId` 不加外键约束：见 §5 风险。

### 1.3 首页查询

子事件就是 `events` 里的普通行，`observeActiveEvents()` 不变。
新增一个今日计数查询，保持「首页不加载记录」的原则：

```sql
SELECT eventId, COUNT(*) AS c
FROM records
WHERE timestamp >= :dayStart
GROUP BY eventId
```

`dayStart` 由 `LocalDate.now().atStartOfDay(zone)` 算出，
与 `DayDiff` 的用法保持一致（按设备当前时区的自然日）。

`statusesLite()` 从 2 个流变 3 个流：events × allStats × todayStats。

---

## 2. 状态计算

`StatusCalculator` 保持单点计算（project.md 约定），按 kind 分派：

```
kind == COURSE     → 聚合子事件：取子事件中最紧急的一档
kind == ON_DEMAND  → 恒定 IDLE，ratio 不参与判定
timesPerDay != null→ 今日进度：done / timesPerDay
其余（PERIODIC）   → 现有 ratio = elapsedDays / baseline
```

`Freshness` 增加一个枚举值：

```kotlin
enum class Freshness { NONE, FRESH, SOON, DUE, IDLE }
```

`IDLE` = 按需事件，只有「上次距今 X 天」，没有「该不该做」。
加枚举值而不是复用 `NONE`：`NONE` 是「还没记过」，
UI 上对两者文案不同（「还没记录过」vs「上次 X 天前」）。

### 2.1 每日频次的计算

```
doneToday = 今日该事件记录数
target    = timesPerDay（>= 1）
doneToday >= target → FRESH（今天齐了）
doneToday == 0       → DUE（今天还没开始）
其余                 → SOON（还差几次）
ratio = doneToday / target   // 仅供进度条，语义与周期型的 ratio 不同
```

**关键取舍**：今日进度与「距上次多久」完全解耦。
一天 3 次的药，早上 8 点吃过一次后是「1/3」，
不会因为距上次只有 4 小时就说「新鲜」——那是另一套口径。
所以频次型事件不看 `lastTimestamp` 的 ratio，只看今天完成了几次。

**不跨天累计**：今天的 `doneToday` 只查当天。
昨天漏吃不会让今天变成「2/3 + 补记」，也不会有连续断签。
铁律 2（不算连续天数、不制造焦虑）在这里的落点就是这一刀。

### 2.2 疗程父事件的状态

父事件自身没有记录（UI 上不允许给它记一笔），状态由子事件聚合：

```
freshness = 子事件中优先级最高者（DUE > SOON > FRESH > NONE > IDLE）
显示文案  = 「今日 2/5」——子事件今日完成数之和 / 目标数之和
```

聚合在 `Repository.statusesLite()` 里做：
events 已经在内存里（几十个），父子配对是 O(n)，
不必为此再加一次 SQL 自连接。

### 2.3 展示层新增字段

`EventStatusLite` 增加：

```kotlin
val parentId: Long?
val parentName: String?     // 子事件在列表里显示为「感冒 2026-09 · 退烧药」
val timesPerDay: Int?
val doneToday: Int
```

`EventStatus`（详情页）同步增加，并额外带 `children: List<EventStatusLite>`。

---

## 3. 交互

### 3.1 新建疗程

编辑页增加「类型」选择（周期 / 按需 / 疗程）。
选「疗程」时：

- 隐藏「期望间隔天数」（疗程没有间隔，只有开始与结束）
- 保存后进入疗程详情页，底部常驻「+ 添加子事件」

子事件继承父的 `tag`，`kind` 由用户选（`PERIODIC` + 频次 / `ON_DEMAND`），
`timesPerDay` 只在子事件上可填。

### 3.2 结束疗程

疗程详情页 →「结束疗程」→ 二次确认（说明会一并归档 N 个子事件及其记录条数）
→ 事务内把父与全部子事件 `isArchived = true`。

恢复走设置页已归档列表：恢复父事件时，其一并归档的子事件一起恢复
（`parentId` 保留着关联，恢复是可靠的）。

**为什么不只给父事件打「已结束」标记、留在首页折叠区**：
那会让首页列表随时间越来越长，与「归档可恢复」这条既有能力重复建设。
复用归档，还能顺带满足「记录仍在时间线与年度回顾」。

### 3.3 首页呈现

- 子事件在首页**独立成行**（钉选时也会出现），名称前缀带父事件名。
- 排序：子事件紧跟其父事件（父在前），组内按 `sortOrder`。
  排序在 `ListViewModel.filtered()` 里做，
  事件量在几十个量级，内存排序开销可忽略。
- 拖拽仍只在同级生效：子事件不能拖出疗程，
  与现有「分组模式不允许跨组拖拽」同一条理由——那是移动，不是排序。

### 3.4 小组件

单事件小组件与列表小组件无需改结构：选中子事件时显示父名前缀。
`ON_DEMAND` 事件在小组件上显示「X 天前」而非红点。

---

## 4. 导出 / 导入

- `JsonStore.Snapshot` 里的 `Event` 自动带上三个新字段，
  序列化处需检查是显式取字段还是反射；旧备份缺字段时按默认值补全
  （`parentId` / `timesPerDay` = null，`kind` = PERIODIC）。
- 导入时若 `parentId` 指向不存在的事件，置为 null
  （升级为独立事件），不丢弃这条数据。
- CSV 导出增加「所属疗程」列；子事件行填父事件名，父事件行留空。

---

## 5. 风险与取舍

| 决策 | 取舍 |
|---|---|
| 疗程复用 events 表 + kind | 少一张表、少一套 CRUD；代价是所有遍历事件的地方都要认识 `COURSE`（不允许记一笔、不参与筛选统计） |
| `parentId` 不加外键约束 | SQLite 外键在导入、恢复、批量归档时容易因顺序问题报错；改为代码层保证（删除父事件时级联处理子事件）。Room 已有 `records` 的外键，那是因为删除链路单一 |
| 今日进度不做跨天累计 | 严格贴合铁律 2；代价是漏服无痕，本次刻意接受 |
| 新增 `IDLE` 枚举 | 所有 `when (freshness)` 分支（`EventCard`、筛选、小组件、年度回顾）都要补分支，Kotlin 的穷尽 `when` 会在编译期把漏网之处报出来，风险可控 |
| 子事件在首页独立成行 | 信息更直接、不用多一层展开；代价是列表变长，靠「父前缀 + 相邻排序」保证可读性 |

## 6. 与既有 spec 的关系

- `event-management`：ADDED「事件层级与疗程」「每日频次设置」「结束疗程」；
  MODIFIED「事件属性」（新增三个字段）。
- `status-calculation`：MODIFIED「三档状态」（变四档 + IDLE）、
  ADDED「按需事件不判逾期」「每日频次状态」「疗程状态聚合」。
- `timeline-review` / `backup-restore`：仅字段透传，行为不变，不出 delta。
