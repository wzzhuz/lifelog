# 列表与分组的排序分开存储

## 对应反馈

「按分组模式组内排序，切回列表模式后，参与排序的分组跑到最前面。
不同模式的排序是否应该分开。」

## 核实结论：是设计缺陷，且比反馈的更严重

### 根因

分组模式保存的是 `rows` 里**所有 Item 的顺序**：

```kotlin
onSaveOrder(rows.filterIsInstance<GroupRow.Item>().map { it.status.event.id })
```

而 `rows` 的构建是：

```kotlin
visible.groupBy { tag }.toList().sortedBy { it.first }   // 按标签名排序
```

**所以在「健康」组里拖一下，
保存下去的其实是「健康组全部 → 汽车组全部 → 个人组全部」
这个按标签排的完整顺序。**

切回列表模式看到的是**所有事件都被按标签重排了一遍**，
不只是「拖过的那组跑到前面」。

### 本质问题

两种模式的排序语义不同，却共用同一个 `sortOrder` 字段：

| 模式 | 排序含义 |
|---|---|
| 列表模式 | **全局顺序**（所有事件混排） |
| 分组模式 | **组内顺序**（先按标签分组，组内再排） |

一个字段当然装不下两种语义。

## 方案对比

| 方案 | 做法 | 代价 | 评价 |
|---|---|---|---|
| **A** | 新增 `sortInGroup` 字段，各管各的 | 多一列 | **推荐**：语义清晰，各自独立记忆 |
| B | 分组模式禁用拖拽 | 无需改表 | 简单，但砍掉刚验证过的可用功能 |
| C | 存「标签 → 顺序」映射 | 新表或 JSON 列 | 语义最准，复杂度过高 |

## 决策

**选 A**（使用者已确认）。

另据使用者确认：**不需要考虑历史数据升级**，
当前均为模拟数据，可随时舍弃。

### 具体做法

1. `EventEntity` 新增 `sortInGroup: Int = 0`
2. `observeActiveEvents()` 保持按 `sortOrder` 排（列表模式用）
3. 新增 `observeActiveEventsGrouped()`：
   ```sql
   ORDER BY tag, sortInGroup, name
   ```
4. 列表模式拖拽 → 写 `sortOrder`
5. 分组模式拖拽 → 写 `sortInGroup`

### 数据库版本

需要 `version` +1 并提供 Migration。
因使用者确认无需保留历史数据，可直接 `fallbackToDestructiveMigration()`，
但**仍会写 migration 分支**，避免将来真有数据时误删。

## 副作用确认

- 两种模式各自记住各自顺序，互不干扰 ✅
- 新增事件在两种模式下都落到末尾 ✅
- 归档 / 恢复不影响任一排序字段 ✅

## 影响范围

**触及 spec**：`specs/event-management/`（排序 Requirement 需区分两种语义）

**是否需要重新安装包**：是

**是否会丢数据**：按使用者确认，模拟数据可弃，
但实现上仍保留 migration 分支以防将来误删

## 与项目铁律是否冲突

- [x] 不违反三条铁律
