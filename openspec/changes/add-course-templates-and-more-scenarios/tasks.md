# 任务清单：疗程模板与子事件场景扩展

change-id：`add-course-templates-and-more-scenarios`

> 顺序：先加数据（模板与预设），再改创建流程，最后做复刻与进度回退。
> 数据先行，因为流程改造依赖它，且数据改动风险最低。

## 1. 数据：疗程模板与子项预设

- [ ] `domain/model/Models.kt`：新增 `CourseTemplate` 数据类
- [ ] `domain/model/Models.kt`：`Templates` 增加 `COURSE_TEMPLATES`
      （感冒 / 术后恢复 / 宠物驱虫 / 中医调理）
- [ ] `domain/model/Models.kt`：`CHILD_PRESETS` 补子项
      —— 换药 2/日、拆线、复查、体内驱虫、体外驱虫、疫苗、中药、针灸
- [ ] 确认 `CHILD_PRESETS` 里「一次性」与「周期型」子项如何表达
      （复用 `targetDays` 为次数，另加 kind 字段，见 design §1）

## 2. 创建流程：模板一键填好

- [ ] `ui/edit/EditViewModel.kt`：新增 `applyCourseTemplate(t)`
      与 `_advancedExpanded` 状态
- [ ] `ui/edit/EditViewModel.kt`：`save()` 在疗程模板场景下回传新 id
- [ ] `ui/edit/EditEventScreen.kt`：新建顶层事件时显示「快速开始」chip 行
- [ ] `ui/edit/EditEventScreen.kt`：图标/颜色/类型/分类收进
      「更多设置（已自动填好）」可折叠区，默认收起
- [ ] `ui/edit/EditEventScreen.kt`：子事件（`parentId != null`）隐藏
      「疗程」类型选项，防止造出三层层级
- [ ] `ui/nav/AppNav.kt`：疗程模板保存后跳疗程详情页（普通事件回首页）

## 3. 名称自动生成

- [ ] `ui/edit/EditViewModel.kt`：`suggestCourseName(prefix)` 生成
      「前缀 + M月d日」
- [ ] `data/LifeLogRepository.kt`：`uniqueCourseName(base)` 撞名追加序号
      （含已归档的同名疗程）
- [ ] 单测：名称格式、撞名序号

## 4. 「再来一次」复刻

- [ ] `data/db/LifeLogDao.kt`：查最近 N 个疗程（含已归档）及其子项数
- [ ] `data/LifeLogRepository.kt`：`duplicateCourse(sourceId, newName)`
      —— 事务内复制疗程 + 子事件（含 timesPerDay），**不复制记录**
- [ ] `ui/edit/EditEventScreen.kt`：「快速开始」下方列最近 3 个疗程
- [ ] 单测：复刻后子事件频次正确、记录数为 0、新子事件指向新疗程

## 5. 混合类型子事件的进度回退

- [ ] `domain/usecase/StatusCalculator.kt`：`courseProgress` 在
      无频次型子事件时，回退显示最紧急子项「名称 · 该做了」
- [ ] 单测：全周期型子事件的疗程能显示状态

## 6. 校验与文档

- [ ] 运行 `python3 scripts/check_kotlin_strings.py`
- [ ] 运行 `python3 scripts/check_docs_consistency.py`
- [ ] `docs/使用说明.md`：补疗程模板与「再来一次」的用法
- [ ] 手动验证：点模板 → 确认四项已填、名称已预填 → 保存 → 进疗程页加药
- [ ] 手动验证：「再来一次」→ 子事件与频次带过来、记录数为 0

## 明确不做

- 自定义疗程模板（用户不能自己存模板，场景写死 4 个）
- 三层层级（疗程 → 子疗程 → 子事件）
- 装修 / 备考 / 健身类场景（见 proposal 判据表）
- 模板自动创建子事件（只给建议 chip，不擅自替用户决定）
- 复刻时复制历史记录（会让新疗程一建成就显示已记 N 次）
