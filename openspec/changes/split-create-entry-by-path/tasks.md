# 任务清单：新建入口按路径分流

change-id：`split-create-entry-by-path`

## 1. 入口分流

- [x] `ListScreen` 首页 FAB 改为弹出「要做什么」二选一
- [x] 新增 `CreatePathSheet`（记一件事 / 开一个疗程）
- [x] `onNote` → 现有编辑页；`onCourse` → 新路由 `COURSE_PICK`

## 2. 「开疗程」独立页

- [x] 新增 `CoursePickViewModel`（选模板 → 生成名字 → 创建）
- [x] 新增 `CoursePickScreen`（模板卡片，含子项清单）
- [x] `LifeLogViewModelFactory` 注册 `CoursePickViewModel`
- [x] `AppNav` 新增 `COURSE_PICK` 路由，创建后进疗程详情页

## 3. 「记件事」精简

- [x] 移除编辑页顶部的「快速开始」模板 chip 行
- [x] 移除 `EditViewModel.applyCourseTemplate` / `suggestCourseName`
- [x] 更多设置默认收起（新建）/ 展开（编辑已有事件）
- [x] 图标、颜色、分类均有默认值，不展开也能保存

## 4. 子项预设按场景收敛

- [x] `Templates.templateForCourseName()` 按疗程名反查模板
- [x] `EditViewModel` 记录 `parentName`
- [x] `ChildPresetChips` 优先显示本场景预设，匹配不到回退全部分组

## 5. 校验

- [x] `scripts/check_kotlin_strings.py`
- [x] `scripts/check_docs_consistency.py`
- [ ] CI 编译与单测通过
- [ ] 出体验包与旧版对比
