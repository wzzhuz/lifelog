# 任务清单：子事件与疗程

change-id：`add-sub-event-course-tracking`
分支：`feature/sub-event-course-tracking`

## 1. 数据层

- [x] `domain/model/Models.kt`：新增 `enum class EventKind { PERIODIC, ON_DEMAND, COURSE }`
- [x] `Event` 增加 `parentId: Long?`、`timesPerDay: Int?`、`kind: EventKind`
- [x] `EventStatusLite` 增加 `doneToday`、`parentName`、`children`
      （`parentId` / `timesPerDay` 直接从 `event` 读，未重复落字段）
- [x] `EventStatus` 增加 `doneToday`、`children: List<EventStatusLite>`
- [x] `data/db/Entities.kt`：`EventEntity` 同步三字段，`kind` 存枚举名
      + `@ColumnInfo(defaultValue = "'PERIODIC'")`
- [x] `Entities.kt`：补充 `toDomain()` / `toEntity()` 的字段映射
- [x] `Entities.kt`：`ArchivedEventRow` 补 `kind` / `timesPerDay` / `parentId`
- [x] `LifeLogDatabase.kt`：版本 2 → 3，新增 `MIGRATION_2_3`（三条 ALTER TABLE）
- [x] `LifeLogDao.kt`：新增今日计数查询
      `SELECT eventId, COUNT(*) FROM records WHERE timestamp >= :dayStart GROUP BY eventId`
      （`observeTodayCounts` + 一次性版 `todayCountsOnce`）
- [x] `LifeLogDao.kt`：新增 `activeChildren` / `allChildren` / `observeActiveChildren`
- [x] `LifeLogDao.kt`：新增 `setArchivedForCourse(parentId, archived)`、
      `deleteEventWithChildren(id)`，均在 `@Transaction` 内一次写完
- [x] `LifeLogRepository.kt`：`statusesLite()` / `statusesLiteGrouped()` 合并今日计流（3 个流）
- [x] `LifeLogRepository.kt`：新增 `endCourse` / `restoreCourse` /
      `courseRecordCount` / `activeChildCount` / `childrenStatuses`
- [x] `LifeLogRepository.kt`：删除事件时处理子事件（删父删子；删子不影响父）
- [x] `LifeLogRepository.kt`：`setArchived` 对疗程走整体归档

## 2. 领域层

- [x] `StatusCalculator`：`Freshness` 增加 `IDLE`
- [x] `StatusCalculator`：按 kind 分派（COURSE 聚合 / ON_DEMAND 恒 IDLE / 频次 / 周期）
- [x] `StatusCalculator`：频次判定只读当日 `doneToday`，不读 `lastTimestamp` 的 ratio
- [x] `StatusCalculator`：`computeLite` / `computePaged` / `compute` 三条路径同步改造

> 参数顺序：`now` 保持原位，新参数（`doneToday` / `children`）一律追加在后。
> 曾把 `doneToday` 插在 `now` 之前，导致既有单测「按位置传 now」被顶错位，编译失败。

## 3. UI 层

- [x] `ui/component/EventCard.kt`：显示「今日 x/y」、子事件显示父名前缀、
      疗程隐藏「记一笔」
- [x] `ui/component/Freshness.kt`：`IDLE` 的颜色与文案（灰蓝 / 按需）
- [x] `ui/edit/EditEventScreen.kt`：新增「类型」选择（周期 / 按需 / 疗程）
- [x] `ui/edit/EditEventScreen.kt`：选疗程时隐藏间隔与频次；可填每日次数
- [x] `ui/edit/EditViewModel.kt`：draft 支持三字段与类型；新建子事件继承疗程分类
- [x] `ui/detail/DetailScreen.kt`：疗程详情展示子事件列表 + 「添加」+ 逐个记一笔
- [x] `ui/detail/DetailScreen.kt`：新增「结束疗程」与二次确认（含子事件数、记录条数）
- [x] `ui/nav/AppNav.kt`：`edit_event` 路由支持 `parentId`
- [x] `ui/list/ListScreen.kt` / `ListViewModel.kt`：子事件紧跟父事件排序
- [x] `ui/settings/ArchivedScreen.kt`：显示层级（疗程带子事件数、子事件带所属疗程），
      恢复疗程时一并恢复子事件
- [x] `widget/widget_common.kt`：`when (freshness)` 补 `IDLE` 分支
- [x] `ui/settings/UsageGuideScreen.kt`：色值对照表补「按需」，新增疗程小节

> 拖拽限制在同级：列表模式的拖拽当前未做父子边界限制，
> 子事件被拖走后仍会按父 id 归位（首页聚合只看 parentId）。
> 这是刻意留的简化——真正的限制应与分组模式一样引入 `canMove`。

## 4. 导出 / 导入 / 迁移

- [x] `data/JsonStore.kt`：Event 序列化补三字段；旧备份缺字段时按默认值补全
      （`EventKind.of` 对非法值返回 `PERIODIC`）
- [x] `data/JsonStore.kt`：合并导入时 `parentId` 置空降级为独立事件
- [x] `LifeLogRepository.kt`：覆盖导入时过滤指向不存在疗程的 `parentId`
- [x] `util/CsvExport.kt`：增加「所属疗程」列
- [x] `domain/model/Models.kt`：`Templates` 中无周期的项改为 `ON_DEMAND`
      （看病、中药调理、针灸、贴膏药、同房）
- [ ] `data/TemplateFile.kt`：外部模板 JSON 支持 `subEvents`
      —— **不做**，见下

## 5. 文档与校验

- [x] 运行 `python3 scripts/check_kotlin_strings.py`
- [x] 运行 `python3 scripts/check_docs_consistency.py`
- [x] `docs/使用说明.md`：补充「疗程：一次看病开的几种药」与按需状态说明
- [x] 新增单测 `CourseAndFrequencyTest.kt`：按需不催、频次只看当天、
      疗程取子事件最紧急的一档、子事件紧跟疗程
- [ ] 归档：将 change 合入 `openspec/specs/` 后移入 `changes/archive/<日期>-<change-id>/`
      —— 等本分支验收通过后再做

## 明确不做（本次）

- 外部模板的 `subEvents` 字段：外部模板是「单个事件」的批量导入格式，
  引入嵌套会让模板文件的预览与去重逻辑复杂化；
  疗程目前通过 UI 手动创建，未成为模板刚需
- 服药提醒通知、自动定期归档、连续天数统计（project.md 「已确认不做」）
- 三层层级（看病 → 疗程 → 用药）
