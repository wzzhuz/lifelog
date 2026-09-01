# 技术设计：排序分开存储

## 根因

分组模式保存的是 `rows` 里**所有 Item 的顺序**：

```kotlin
onSaveOrder(rows.filterIsInstance<GroupRow.Item>().map { it.status.event.id })
```

而 `rows` 构建自：

```kotlin
visible.groupBy { tag }.toList().sortedBy { it.first }   // 按标签名
```

**在「健康」组里拖一下，保存下去的是
「健康组全部 → 汽车组全部 → 个人组全部」这个完整顺序。**

所以切回列表模式看到的不是「拖过的那组跑到前面」，
而是**所有事件都被按标签重排了一遍**。

## 本质

两种模式排序语义不同，却共用一个字段：

| 模式 | 语义 |
|---|---|
| 列表 | 全局顺序 |
| 分组 | 先按标签分组，组内再排 |

## 方案

新增 `sortInGroup` 字段，两个字段各管各的。

### 数据库升级

```kotlin
version = 2
MIGRATION_1_2:
  ALTER TABLE events ADD COLUMN sortInGroup INTEGER NOT NULL DEFAULT 0
  UPDATE events SET sortInGroup = sortOrder
```

初值取 `sortOrder`，升级后两种模式表现一致，用户无感。

使用者确认模拟数据可弃，但**仍保留 migration 分支**——
避免将来有真实数据时被 `fallbackToDestructiveMigration` 静默清空。

### 查询

```sql
-- 列表模式
ORDER BY isPinned DESC, sortOrder ASC, name ASC

-- 分组模式
ORDER BY CASE WHEN tag IS NULL OR tag = '' THEN 1 ELSE 0 END,
         tag ASC, isPinned DESC, sortInGroup ASC, name ASC
```

`CASE` 的作用：SQLite 默认 NULL 最小，
不加这行「未分类」会凭空跑到最前。

### 双数据流

ViewModel 按布局模式切换查询：

```kotlin
layoutFlow.flatMapLatest { mode ->
    if (mode == GROUPED) repo.statusesLiteGrouped()
    else repo.statusesLite()
}
```

不能共用一个流——两个字段各自独立，流必须跟着模式走。

## 副作用确认

- 两种模式各自记忆，互不干扰
- 新增事件在两种模式下都落到末尾
- 归档 / 恢复不影响任一排序字段
