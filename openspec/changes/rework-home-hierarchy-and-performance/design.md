# 技术设计：首页层级、卡片密度与性能重构

change-id：`rework-home-hierarchy-and-performance`

## 0. 数据库结构设计（前置）

用户要求"前期先把数据库结构设计好，避免以后不好扩展"。
本节给出完整的 schema 审视：**本次改什么、不改什么、以及各自为什么**。

### 0.1 目标 schema

**events**（低频写入，几十行，读取极频繁）

| 列 | 本次动作 | 说明 |
|---|---|---|
| `id` | 保留 | 主键 |
| `name` / `emoji` / `colorArgb` | 保留 | 展示属性 |
| `kind` | 保留 | 事件类型，见 0.3 |
| `targetDays` / `timesPerDay` | 保留 | 周期 / 频次参数 |
| `parentId` | 保留 | 疗程层级（两层，不再加深） |
| `tag` | 保留 | 自由文本，见 0.4 |
| `note` / `isPinned` / `isArchived` | 保留 | — |
| `sortOrder` | 保留 | 分组模式删除后唯一的手工排序字段 |
| `sortInGroup` | **删除** | 随分组模式一并移除 |
| `createdAt` | 保留 | — |
| `lastTs` | **新增** | 派生：最近一次记录时间 |
| `recordCount` | **新增** | 派生：累计记录条数 |
| `lastRecordDay` | **新增** | 派生：最近记录所在日序 `yyyyMMdd` |
| `todayCount` | **新增** | 派生：当日已完成次数 |
| `photoCount` | **不做** | 见 0.5 |

索引：`name`（导入去重）、`tag`（分类筛选）、`parentId`（子事件查询）。

> `parentId` 当前**无索引**。子事件查询走 `WHERE parentId = ?`，
> 事件数只有几十个时全表扫描也无所谓；但索引成本近乎为零，
> 本次顺手加上，避免以后事件数涨上来才发现。

**records**（高频写入，只增，消费方式固定）

| 列 | 动作 | 说明 |
|---|---|---|
| `id` / `eventId` / `timestamp` | 保留 | — |
| `note` | 保留 | 搜索目标列，见 0.6 |
| `photoName` | 保留 | 见 0.5 |
| `loggedAt` | 保留 | 录入时刻，与 `timestamp` 语义不同（补录场景有区分价值） |

索引：`(eventId, timestamp)`、`(timestamp)` —— 均已存在，实测够用。

### 0.2 分表：明确不做

实测结论见 `proposal.md`「数据库结构专项评估（第二轮）」：
10 条读写路径全部实测，**唯一的真瓶颈是 ⑦ 全表读**（时间线页面），
200 万条时 2.5 秒；而它靠"分页"解决，与表怎么切分无关。
分表后它反而要跨表 UNION + 排序，更慢。

**records 保持单表**，这是本设计明确记录并坚持的决定。

### 0.2.1 时间线分页（新增关键任务）

**现状**：`TimelineViewModel` → `repo.snapshotFlow()` → `allRaw()` →
`dao.allRecords()`，即 `SELECT * FROM records ORDER BY timestamp DESC`，
**无任何 LIMIT**。每次数据变化都全量重读。

实测：5 万条 38ms（已超一帧），200 万条 2564ms。

**改造**：新增按页查询，时间线改为分页加载（复用详情页已有的分页模式）：

```sql
SELECT * FROM records
ORDER BY timestamp DESC
LIMIT :limit OFFSET :offset
```

实测效果（200 万条）：首页 0.03ms / 第 100 页 0.12ms / 末页 51.4ms。

`snapshotFlow()` 的订阅方只有时间线一处，改造面可控。
**导出备份仍走全表读**——它是低频主动操作且在后台线程，属于唯一豁免。

### 0.2.2 架构约束：禁止无 LIMIT 的 records 全表查询

写入 spec 与代码注释：

> 除导出备份外，SHALL NOT 出现不带 `LIMIT` 的 `SELECT * FROM records`。
> 需要遍历时 SHALL 改为分页，或改为 SQL 聚合（只取 COUNT/MIN/MAX）。

当前违反此约束的路径及处置：

| 路径 | SQL | 处置 |
|---|---|---|
| `TimelineViewModel` → `snapshotFlow()` | 全表读 | **改为分页** |
| `exportJson()` → `allRaw()` | 全表读 | 保留（低频豁免） |
| `SettingsScreen` → `allRaw()` | 全表读 | **改用 SQL 聚合取统计值** |
| `deleteEvent` → `recordsOf(id)` | 单事件全量 | **只查 photoName 列**（见 0.2.3） |
| `importJson` 合并 → `allRecords()` | 全表读 | 保留（低频豁免） |
| `statusOfOnce()` → `recordsOf(id)` | 单事件全量 | **改为只取 MAX(timestamp)** |

### 0.2.3 `deleteEvent` 的轻量化

当前删事件要先 `recordsOf(id)` 全量读出来筛 `photoName`，
单事件 10 万条时实测 71.9ms（且把全部行搬进内存）。

改为只取需要的列：

```sql
SELECT photoName FROM records WHERE eventId = ? AND photoName IS NOT NULL
```

照片是少数，这样只返回几行。

### 0.3 `kind` 存 String 而非 TypeConverter：保持现状

上一版选择存枚举名字符串，理由是"少一个注解处理器路径"。
严格说 `EventKind.of()` 已提供兜底，类型安全由 Kotlin 侧保证，
数据库里理论上可写入任意字符串。

**本次不改。** 理由：改动需要重写迁移逻辑与全部回读路径，
风险不为零，而收益仅是洁癖层面的。若未来引入同步（需要跨端校验），
再考虑加 `CHECK` 约束或改用 TypeConverter。

### 0.4 `tag` 不独立成表：过度设计

当前 tag 是自由文本，直接存在 events 上。
若要支持"重命名标签""标签配色""标签排序"，确实需要独立 tag 表。

**现在不做**：用户未提出这类需求，独立表会让每次读取都要 JOIN，
而首页是性能敏感路径。**触发条件**：用户要求重命名或管理标签时再迁移。

### 0.5 照片文件的孤儿问题（已知隐患，本次顺手修）

`photoName` 存在 `records` 上，但文件在私有目录，
**外键 CASCADE 删除记录时不会清理文件**。当前 `deleteEvent` 手动清理了，
但 `importJson` 覆盖导入走 `db.clearAllTables()`，
会清空全部记录而照片文件全部残留。

本次**不做完整修复**（需要维护引用计数，成本高），
但采取两个低成本动作：

1. 覆盖导入前，先遍历清理 `photos/` 下全部文件
2. 在 `PhotoStore` 注释中显式记录这条约束

**不做 `photoCount` 派生列**：照片数量不是高频读取项，
且需要额外的引用计数维护，收益不抵复杂度。

### 0.6 搜索：不加 FTS5

`LIKE '%x%'` 全表扫描，50 万条时 195ms。
FTS5 能解决，但会写入放大、增加体积、引入第二套搜索代码路径。

**不做。** 现有 150ms 防抖已经让搜索变成低频操作，
195ms 在"主动搜索"语境下可接受。
**触发条件**：记录数 > 50 万，或用户明确抱怨搜索慢。

### 0.7 明确不引入的列

| 候选 | 不引入的理由 |
|---|---|
| `updatedAt` | 无同步需求，单机无冲突解决场景 |
| `deletedAt`（软删除） | 归档已覆盖"暂时不看"；真删除是用户明确意图 |
| `version`（乐观锁） | 单机单进程写入，无并发冲突 |
| 三级层级（`grandParentId`） | 两层（疗程→子事件）已覆盖真实场景，第三层是臆想需求 |

### 0.8 未来扩展的检查清单

以下变化发生时，需重新审视 schema（不是现在做）：

- 引入云同步 → 需要 `updatedAt`、`deletedAt`、冲突解决策略
- 记录数突破 50 万 → 考虑 FTS5 或搜索范围限制
- 需要标签管理 → 独立 tag 表 + events.tagId 外键
- 需要记录分类/多维度统计 → 可能要在 records 上加 `type` 列

---

## 0.9 性能实测基线（第二轮，全部 10 条读写路径）

80 事件（5 个高频占 60%），记录跨 5 年，索引齐全，单位毫秒：

| # | 路径 | 5 万 | 50 万 | 200 万 | 归口 |
|---|---|---|---|---|---|
| ① | 首页 GROUP BY | 3.7 | 37.1 | 161.3 | 派生列消除 |
| ② | 详情页分页 20 | 0.0 | 0.0 | 0.0 | 已达标 |
| ③ | 年度回顾 | 7.3 | 95.3 | 514.6 | 低频，观察 |
| ④ | LIKE 搜备注 | 13.7 | 204.3 | 1374.2 | 防抖 + FTS5 触发条件 |
| ⑤ | 今日计数 | 1.0 | 11.1 | 51.8 | 派生列消除 |
| ⑥ | 归档 JOIN | 0.0 | 0.0 | 0.0 | 已达标 |
| ⑦ | **全表读** | **38.0** | **476.2** | **2564.7** | **改分页（0.2.1）** |
| ⑧ | 单事件全量 | 4.3 | 58.7 | 313.7 | 改轻量查询（0.2.3） |
| ⑨ | 插入 1 条 | ~0 | ~0 | ~0 | 已达标 |
| ⑩ | 删除 1 条 | ~0 | ~0 | ~0 | 已达标 |

改造完成后预期：**除 ④ 搜索与 ③ 年度回顾外，全部 ≤ 0.12ms，
且与记录数无关。**

---

## 1. 现状：首页数据流

```
observeActiveEvents()  ──┐
observeAllStats()      ──┼─→ combine ─→ buildLite() ─→ ListUiState.all
todayCounts()          ──┘                                    │
                                                             ↓
                              filtered() → withChildrenGrouped() → visible
```

`observeAllStats` 与 `todayCounts` 都是对 `records` 表的 GROUP BY。
**任何一次写入 → 两张表各一次全表聚合 → 整个列表重建。**

被调用的点共 6 处，其中 4 处在打开详情页时同时活跃：

| 调用点 | 是否需要全表聚合 |
|---|---|
| `statusesLite()`（首页） | 是，重构后不再需要 |
| `statusesLiteGrouped()`（分组） | 是 —— **本次删除** |
| `statusOfPaged()` 内的 children | 仅疗程需要 |
| `childrenStatuses()`（详情页） | **非疗程完全不需要** ← 最大浪费 |
| `statusesLiteOnce()`（小组件） | 是，重构后不再需要 |
| `statusOfOnce()`（单事件小组件） | 是，重构后不再需要 |

## 2. 派生列方案

### 2.1 新增列

`events` 表（DB 3 → 4）：

| 列 | 类型 | 含义 |
|---|---|---|
| `lastTs` | INTEGER NULL | 最近一次记录时间 |
| `recordCount` | INTEGER NOT NULL DEFAULT 0 | 累计记录条数 |
| `lastRecordDay` | INTEGER NULL | 最近一次记录所在的"日序"（`yyyyMMdd` 整数） |
| `todayCount` | INTEGER NOT NULL DEFAULT 0 | 当日已完成次数 |

`lastRecordDay` 用 `yyyyMMdd` 整数而非时间戳：
判断"是不是今天"只需一次整数相等比较，且不受具体时刻影响。

### 2.2 一致性维护

写入路径统一走 Repository，每处都在**事务内**维护：

```kotlin
// 新增记录
db.withTransaction {
    dao.insertRecord(...)
    val day = dayKeyOf(timestamp)          // 设备时区
    val e = dao.eventById(eventId) ?: return
    val sameDay = e.lastRecordDay == day
    dao.updateDerived(
        id          = eventId,
        lastTs      = maxOf(e.lastTs ?: 0, timestamp),
        recordCount = e.recordCount + 1,
        lastRecordDay = day,
        todayCount  = if (sameDay) e.todayCount + 1 else 1
    )
}
```

- **删除记录**：`recordCount - 1`；`todayCount` 重算当日 COUNT；
  若删的是最后一条，`lastTs` 回退到前一条（查一次 `MAX(timestamp)`）
- **导入（覆盖）**：全部导入完后调 `recomputeDerived()`
- **导入（合并）**：同上，重算一次即可

因为记录条数少（用户明确说"数据很少"），
`recomputeDerived()` 直接一条 `UPDATE ... FROM (SELECT ... GROUP BY)` 完成，不做增量。

### 2.3 兜底

`recomputeDerived()` 是**幂等全量重算**，任何一处漏更新都能靠它恢复。
设置页暴露一个「重建派生数据」入口（放在已有统计区块旁），
不是常规功能，只在怀疑数据不一致时用。

### 2.4 为什么不用 TRIGGER

`todayCount` 的"今天"边界必须是**设备时区**的 00:00，
而 SQLite 的 `date('now')` 恒为 UTC。
在 trigger 里写时区换算既脆弱又不可测，
因此派生值由 Kotlin 侧在写入时算好再落库。

> 这带来一个约束：**任何绕过 Repository 直接写 `records` 的路径都会破坏一致性**。
> 当前不存在这类路径（导入也走 Repository），需在代码注释里显式声明这条约束。

## 3. 首页查询的退化

重构后：

```kotlin
fun statusesLite(): Flow<List<EventStatusLite>> =
    dao.observeActiveEvents().map { list -> buildLite(list.map { it.toDomain() }) }
```

- 单表、无 JOIN、无 GROUP BY
- `buildLite` 不再需要 `stats` 与 `today` 两个参数，签名简化
- `StatusCalculator.computeLite` 的 `count` / `firstAsc` / `lastAsc` / `doneToday`
  全部改为从 `Event` 派生列直接读取

`StatusCalculator` 的对外签名保持不变（避免波及调用点）：
由 Repository 把派生列填进 `Event` 对象再传入。

> `Event` 是领域模型，往里塞 `recordCount` 这类派生存储列有洁癖上的争议。
> 取舍：让 `Event` 多带四个字段，换来 `StatusCalculator` 与全部调用点、单测零改动，值得。
> 字段命名沿用已有的语义，并在 KDoc 标注"由 Repository 维护，不要手工写"。

### 3.1 消灭 O(n²)

当前 `buildLite` 对每个疗程做一次 `lite.filter { it.event.parentId == st.event.id }`：

```kotlin
lite.map { st -> if (st.event.kind != COURSE) st
                 else aggregateCourseLite(st, lite.filter { ... }) }   // ← O(n²)
```

改为一次 `groupBy { it.event.parentId }` 后查表。事件数只有几十个，
原本也不至于卡，但它每次流发射都跑，属于顺手修掉的浪费。

## 4. 详情页订阅收敛

```kotlin
val children: StateFlow<List<EventStatusLite>> =
    if (isCourse) repo.childrenStatuses(eventId).stateIn(...)
    else MutableStateFlow(emptyList())
```

`isCourse` 在 ViewModel 构造时**一次性查库**得到（不订阅）。
非疗程事件的详情页因此完全不碰 `records` 的聚合查询。

同时删除 `statusOfPaged` 内部为 children 拼的那个三流 combine——
它已经被 `childrenStatuses` 覆盖，属于重复订阅。

## 5. 「今天」起点共享化

```kotlin
private val dayStart: StateFlow<Long> = flow {
    while (true) { emit(startOfToday()); delay(60_000L) }
}.distinctUntilChanged().stateIn(...)   // 仓库级单例
```

需要一个仓库级 `CoroutineScope`（`SupervisorJob + Dispatchers.Default`）。
`todayCounts()` 改为 `dayStart.flatMapLatest { dao.observeTodayCounts(it) }`。

重构完成后 `todayCounts()` 只剩导入/重算等少数路径在用，
首页与详情页都已改为读 `events.todayCount`，因此这个流的订阅点大幅减少。

## 6. UI：子事件视觉层级

`EventCard` 增加 `indent: Boolean`（由 `event.parentId != null` 推出）：

```
┌─ 💊 退烧药                    ← 父卡片：无缩进，58dp
│  今日 1/2
│
├─ 💊 退烧药            ← 子卡片：缩进 20dp，48dp，左侧 2dp 引导线
│  今日 1/2 · 感冒 2026-09
```

引导线用 `Box(Modifier.width(2.dp).fillMaxHeight())` 画在缩进留出的 20dp 内，
颜色取父事件的 `freshnessColor` 低透明度版本，保持与色条同源。

> 注意：引导线是**每个子卡片各画一段**，不做跨卡片的连续竖线。
> 连续竖线需要在 LazyColumn 层面测量相邻项，会引入测量耦合与滚动时的重绘抖动。
> 分段线在视觉上足够成组，且实现隔离在卡片内部。

归属提示改在副标题末尾，用 `onSurfaceVariant` 弱化色：

```
今日 1/3 · 感冒 2026-09
```

## 7. UI：卡片密度

| 项 | 现状 | 改后 |
|---|---|---|
| 紧凑卡片高度 | 72dp | 58dp（子事件 48dp） |
| 打钩按钮 | 36dp | 32dp |
| 横向 padding | start 12 / end 8 | start 12 / end 6 |
| 色条高度 | 写死 72dp | `fillMaxHeight` |
| 舒适卡片高度 | 112dp | 96dp |

两行文字实测约 38dp（`titleSmall` 20dp + 2dp + `labelSmall` 16dp），
58dp 给出上下各 10dp 呼吸空间。

## 8. 模板：可重复继承

### 8.1 移除「吃药」

`Templates.ALL` 中的「吃药」删除。它作为独立长期事件没有意义——
吃药总是从属于某次疗程。

### 8.2 新增子事件预设

```kotlin
object Templates {
    val ALL: List<Template>            // 长期事件，按名去重
    val CHILD_PRESETS: List<Template>  // 用药/子事件预设，允许重复添加
}
```

`CHILD_PRESETS` 示例：退烧药(3/日)、消炎药(2/日)、止咳糖浆(3/日)、
外用药膏(2/日)、维生素(1/日)。

在疗程详情页「添加子事件」时以 chip 呈现，点击后：
名字 + 默认每日次数填好，用户可直接保存或改名。

### 8.3 去重规则收窄

```kotlin
private suspend fun insertTemplates(templates: List<Template>) {
    // 只对顶层事件按名去重；子事件允许同名（不同疗程本就该有同名药）
    val existing = dao.allEvents().filter { it.parentId == null }.map { it.name }.toSet()
    ...
}
```

## 9. 减法：删除分组模式

删除清单：

- `HomeLayoutMode.GROUPED` 枚举项（保留 `COMPACT` / `COMFORT`）
- `Event.sortInGroup`、`EventEntity.sortInGroup`
- `LifeLogDao.observeActiveEventsGrouped` / `updateSortInGroup` / `updateSortInGroups`
- `LifeLogRepository.statusesLiteGrouped` / `saveGroupOrder`
- `ListViewModel.saveGroupOrder` / `layoutFlow`（不再需要按模式切换查询）
- `ListScreen` 的 `GroupedList` / `GroupRow` / `GroupHeader` 及 `collapsedTags` 参数
- `HomeLayoutPrefs.collapsedFlow` / `toggleCollapsed`
- `DragSortLazyColumn` 的 `canMove` 参数

`ListViewModel` 的 `statuses` 因此不再需要 `flatMapLatest` 按模式分叉，
直接订阅单条流即可。

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 派生列与实际记录不一致 | 首页数字错 | `recomputeDerived()` 幂等重算 + 设置页入口；写入路径集中在 Repository 并注释约束 |
| 删除记录时 `lastTs` 回退算错 | 状态误判 | 删除后直接用 `MAX(timestamp)` 重取，不靠减法推 |
| 跨时区 / 跨天时 `todayCount` 过期 | 显示的今日数偏旧 | `lastRecordDay` 不等今天时，`todayCount` 一律按 0 读（读时校正，不依赖写时） |
| 去掉分组模式后用户不习惯 | 功能缺失感 | 分类筛选行仍在，可按分类看；若反馈强烈再加回（成本低于维护两套排序） |
| 冷启动仍慢（release 包验证后） | 体验不达标 | 先排除 debug 包因素；剩余部分单独立项做 baseline profile |
| 记录数增长后首页又变慢 | 性能回退 | 重构后首页复杂度 O(事件数)，与记录数解耦；`recomputeDerived()` 兜底 |
| 搜索随记录数变慢 | 搜索卡顿 | 50 万条时 195ms，已有 150ms 防抖；超阈值后单独立项做 FTS5 |

> **`todayCount` 读时校正**是关键：
> 写入时维护的值可能因为 App 跨天未打开而过期，
> 读取时一旦发现 `lastRecordDay != 今天`，就直接返回 0，不写回。
> 这样即使 App 挂了一整夜，第二天打开也不会把昨天的次数算进来。

## 11. 验证方式

- 单测：派生列维护（新增 / 删除 / 跨天）、`todayCount` 读时校正、
  子事件缩进判定、按需事件指标分支
- 手动验证路径：
  新建疗程 → 加两种药 → 首页确认缩进与引导线 → 各记一次确认「今日 1/2」→
  结束疗程 → 归档页确认层级提示 → 恢复
- 性能对比：**必须同时测 debug 与 release**，记录冷启动与详情页进出耗时
- 改动涉及 Kotlin 源码拼接时运行 `scripts/check_kotlin_strings.py`；
  涉及选型/功能增删时运行 `scripts/check_docs_consistency.py`
