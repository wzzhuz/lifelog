# 任务清单：首页层级、卡片密度与性能重构

change-id：`rework-home-hierarchy-and-performance`

> 顺序按「先减后加」：先把分组模式删干净，再做层级与密度，最后动性能结构。
> 每完成一组就推一次分支看 CI，避免堆一大坨改动后定位不到问题。

## 1. 减法：删除分组模式

- [ ] `domain/model/Models.kt`：`HomeLayoutMode` 相关枚举删 `GROUPED`
      （枚举在 `data/HomeLayoutMode.kt`）
- [ ] `domain/model/Models.kt`：`Event` 删 `sortInGroup`
- [ ] `data/db/Entities.kt`：`EventEntity` 删 `sortInGroup`；`ArchivedEventRow` 同步
- [ ] `data/db/LifeLogDao.kt`：删 `observeActiveEventsGrouped`、
      `updateSortInGroup`、`updateSortInGroups`
- [ ] `data/LifeLogRepository.kt`：删 `statusesLiteGrouped`、`saveGroupOrder`
- [ ] `ui/list/ListViewModel.kt`：删 `saveGroupOrder`、`layoutFlow` 分叉；
      `statuses` 直接订阅单条流
- [ ] `ui/list/ListScreen.kt`：删 `GroupedList` / `GroupRow` / `GroupHeader`，
      去掉 `collapsedTags` / `onToggleCollapse` 参数
- [ ] `data/HomeLayoutPrefs.kt`：删 `collapsedFlow` / `toggleCollapsed`
- [ ] `ui/component/DragSortLazyColumn.kt`：删 `canMove` 参数
- [ ] `ui/nav/AppNav.kt`：去掉传给 `ListScreen` 的分组相关参数

## 2. 首页层级（问题 1）

- [ ] `ui/component/EventCard.kt`：新增 `indent` 参数（由 `parentId != null` 推出）
- [ ] 子事件卡片：左侧 20dp 缩进 + 2dp 引导线（色取 `freshnessColor` 低透明）
- [ ] 子事件高度档位：紧凑 48dp（父 58dp）
- [ ] 副标题末尾追加归属：`今日 1/3 · 感冒 2026-09`（弱化色）
- [ ] 名字前缀「父 · 子」移除（改由副标题承载）

## 3. 卡片密度（问题 3）

- [ ] `EventCard.kt`：紧凑卡片 `72dp` → `58dp`，子事件 `48dp`
- [ ] 打钩按钮 `36dp` → `32dp`；横向 padding `end 8dp` → `6dp`
- [ ] 色条改 `fillMaxHeight`（不再写死高度）
- [ ] 舒适卡片 `112dp` → `96dp`，同步复核

## 4. 按需事件详情页指标（问题 2）

- [ ] `ui/detail/DetailScreen.kt`：StatBox 按 kind 分派
      —— `ON_DEMAND` 显示「累计次数 / 首次记录」两宫格，隐藏判断基准
- [ ] 移除按需事件的「平均间隔」与「预计下次」文案
- [ ] 顶部大数字区保留「上次距今」（按需事件唯一该回答的问题）

## 5. 模板可重复继承（问题 4）

- [ ] `domain/model/Models.nt` → `Models.kt`：从 `Templates.ALL` 移除「吃药」
- [ ] 新增 `Templates.CHILD_PRESETS`（用药预设，带默认每日次数）
- [ ] `data/LifeLogRepository.kt`：`insertTemplates` 只对 `parentId == null`
      的事件按名去重
- [ ] 疗程详情页「添加子事件」页：以 chip 呈现 `CHILD_PRESETS`，
      点击填入名字与默认次数，允许重复添加

## 6. 性能：派生列与单表首页（问题 5 · 结构性）

- [ ] `data/db/Entities.kt`：`EventEntity` 增加 `lastTs` / `recordCount` /
      `lastRecordDay` / `todayCount`
- [ ] `domain/model/Models.kt`：`Event` 增加同四字段，KDoc 标注
      「由 Repository 维护，勿手工写」
- [ ] `data/db/LifeLogDatabase.kt`：版本 3 → 4，`MIGRATION_3_4` 加列
- [ ] `data/db/LifeLogDao.kt`：新增 `updateDerived(...)` 与 `recomputeDerived()`
- [ ] `data/LifeLogRepository.kt`：新增/删除记录在事务内维护派生列
- [ ] `data/LifeLogRepository.kt`：导入（覆盖与合并）后调 `recomputeDerived()`
- [ ] `data/LifeLogRepository.kt`：`todayCount` 读时校正
      （`lastRecordDay != 今天` 时按 0 读）
- [ ] `statusesLite()` 退化为单表查询；`buildLite` 去掉 stats/today 参数
- [ ] `buildLite` 的 O(n²) `filter` 改为一次 `groupBy`
- [ ] 删除 `observeAllStats` 在首页的使用（保留给重算/导入）
- [ ] `ui/settings/SettingsScreen.kt`：设置页加「重建派生数据」入口

## 7. 性能：订阅收敛与共享

- [ ] `ui/detail/DetailViewModel.kt`：`children` 仅在 `kind == COURSE` 时订阅
- [ ] `data/LifeLogRepository.kt`：删除 `statusOfPaged` 内重复的 children 三流 combine
- [ ] `data/LifeLogRepository.kt`：`dayStartFlow` 改为仓库级 `stateIn` 单例
      （需要仓库级 `CoroutineScope`）
- [ ] `statusesLiteOnce()` / `statusOfOnce()`（小组件）改读派生列

## 8. 首屏体验

- [ ] `ui/list/ListViewModel.kt`：`ListUiState` 增加 `loaded` 标记，
      未加载时显示轻量占位，避免 EmptyState 闪烁
- [ ] 记录冷启动耗时基线（debug 与 release 各一次，用于对比）

## 9. 校验与文档

- [ ] 运行 `python3 scripts/check_kotlin_strings.py`
- [ ] 运行 `python3 scripts/check_docs_consistency.py`
- [ ] 新增单测：派生列维护（新增/删除/跨天）、`todayCount` 读时校正
- [ ] 新增单测：按需事件指标分支、子事件缩进判定
- [ ] `docs/使用说明.md`：更新紧凑模式与模板说明（吃药改为疗程内添加）
- [ ] 手动验证路径：新建疗程 → 加药 → 记一笔 → 结束疗程 → 归档 → 恢复

## 10. 消灭全表读（第二轮实测新增，优先级与本批性能项同级）

> 实测发现 ⑦ 全表读（时间线页面）是最严重瓶颈：
> 5 万条 38ms / 200 万条 2564ms，比首页 GROUP BY 严重 10 倍。

- [ ] `data/db/LifeLogDao.kt`：新增 `recordsGlobalPage(limit, offset)`
      （`ORDER BY timestamp DESC LIMIT ? OFFSET ?`）
- [ ] `data/LifeLogRepository.kt`：`snapshotFlow()` 改为分页加载
- [ ] `ui/timeline/TimelineViewModel.kt`：改为分页模型（复用详情页分页写法）
- [ ] `ui/timeline/TimelineScreen.kt`：加「加载更早」入口
- [ ] `data/LifeLogRepository.kt`：`deleteEvent` 只查 photoName
      （`SELECT photoName WHERE eventId=? AND photoName IS NOT NULL`），
      不再全量读事件记录
- [ ] `data/db/LifeLogDao.kt`：新增取 photoName 的轻量查询
- [ ] `data/LifeLogRepository.kt`：`statusOfOnce()` 不再全量读记录，
      改取 `MAX(timestamp)` 与当日 COUNT
- [ ] `ui/settings/SettingsScreen.kt`：`allRaw()` 改为 SQL 聚合取统计值
- [ ] 代码注释写入约束：除导出外禁止无 LIMIT 的 records 全表查询

## 11. 数据库结构（前置项，与 1 并行）

- [ ] `data/db/Entities.kt`：`EventEntity` 增加 `parentId` 索引
- [ ] 覆盖导入前清理 `photos/` 全目录（防照片孤儿）
- [ ] `data/PhotoStore.kt`：注释记录「CASCADE 删除不清理文件」的约束
- [ ] 确认 `kind` 保持 String 存储（不改 TypeConverter）
- [ ] 确认 `tag` 不独立成表（记录在 design 0.4）

## 明确不做

- 服药提醒通知、连续天数统计、打卡（与铁律 2 冲突）
- 首页宫格模式（已确认不做）
- baseline profile / R8（先靠 release 包验证真实差距）
- 三层层级（维持两层：疗程 → 子事件）
- **records 分表**（实测 50 万条时分页 0.01ms / 年度回顾 5.3ms，
  分表对唯一的 LIKE 热点毫无帮助，却会让搜索、时间线、导出全部跨表）
- **FTS5 全文索引**（写入放大 + 第二套搜索路径；
  触发条件：记录数 > 50 万或用户明确抱怨搜索慢）
- **`updatedAt` / 软删除 / 乐观锁**（单机无同步需求）
- **`tag` 独立表**（触发条件：用户要求重命名或管理标签）
- **`photoCount` 派生列**（引用计数维护成本高于收益）
