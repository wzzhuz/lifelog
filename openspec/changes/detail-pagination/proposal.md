# 详情页时间线分页

## 对应反馈

「详情页显示的是全部，分页没有启用。我担心记录多了进详情页会很慢。」

## 核实结论：担心成立，且存在 O(n²) 陷阱

分页确实一直没接（DAO 的 `recordsPage` 早已写好，UI 层零调用）。
但真正的性能地雷是**间隔计算**：

```kotlin
items(s.records.sortedByDescending { it.timestamp }) { rec ->
    RecordRow(gapMillis = gapBefore(s.records, rec), ...)
}

private fun gapBefore(recordsAsc: List<Record>, r: Record): Long? {
    val idx = recordsAsc.indexOfFirst { it.id == r.id }   // O(n)
    ...
}
```

每行一次全表查找，整体 **O(n²)**：

| 记录数 | 比较次数 |
|---|---|
| 100 | 1 万 |
| 1,000 | **100 万** |
| 5,000 | **2,500 万** |

且发生在**渲染过程中**，直接卡住 UI 线程。

附带问题：`sortedByDescending` 每次重组都重排一遍。

## 做什么

### 1. 分页加载

- 默认加载最近 20 条
- 滑动到底部自动加载下一批
- 顶部显示「共 N 条记录，显示最近 20 条」

### 2. 修掉 O(n²)

**不需要 `indexOfFirst`。** 排序后相邻两项直接相减即得间隔：

```
已按时间倒序 → gap(i) = time(i) - time(i+1)
```

从 O(n²) 降为 O(n)。

### 3. 统计信息改走 SQL 聚合

⚠️ **关键约束**：分页后内存里只有 20 条，
但「累计次数 / 平均间隔 / 最短间隔 / 预测下次」需要**全量数据**。

这些不能靠遍历内存里的分页结果，必须改为 SQL 聚合：

| 统计项 | SQL |
|---|---|
| 累计次数 | `COUNT(*)` |
| 首次 / 最近 | `MIN(timestamp)` / `MAX(timestamp)` |
| 平均间隔 | `(MAX - MIN) / (COUNT - 1)` |

**平均间隔为什么能用首末差**：
相邻间隔之和 = 末次 − 首次，除以间隔个数即得均值。
不需要逐条两两计算，一次聚合就能拿到。

### 4. 小组件同步

`statusesLiteOnce()` 已走 SQL 聚合，不受影响，但需确认无需改动。

## 不做什么

- ❌ 不做虚拟滚动（分页已足够）
- ❌ 不做记录归档（首页走聚合查询，性能收益为零）

## 影响范围

**触及 spec**：`specs/timeline-review/`（新增分页 Requirement）

**是否需要重新安装包**：是

**是否会丢数据**：否

## 与项目铁律是否冲突

- [x] 不违反三条铁律
