# 技术设计：详情页分页

## O(n²) 的根因

```kotlin
items(s.records.sortedByDescending { it.timestamp }) { rec ->
    RecordRow(gapMillis = gapBefore(s.records, rec), ...)
}

private fun gapBefore(recordsAsc: List<Record>, r: Record): Long? {
    val idx = recordsAsc.indexOfFirst { it.id == r.id }   // O(n)
    ...
}
```

每行一次全表查找，整体 O(n²)，且发生在渲染过程中。

## 修法

排序后**相邻两项直接相减**即得间隔，不需要 `indexOfFirst`：

```kotlin
itemsIndexed(s.records, key = { _, r -> r.id }) { i, rec ->
    val older = s.records.getOrNull(i + 1)
    RecordRow(gapMillis = if (older != null) rec.timestamp - older.timestamp else null, ...)
}
```

O(n²) → O(n)。顺带去掉每次重组的 `sortedByDescending`
（顺序由 ViewModel 保证）。

## 分页与统计的矛盾

分页后内存里只有 20 条，但统计需要全量：

| 统计项 | 若用分页结果 | 正确做法 |
|---|---|---|
| 累计次数 | 得到 20 而非真实总数 | `COUNT(*)` |
| 平均间隔 | 只覆盖最近一段，失真 | 首末差 ÷ 间隔数 |

**平均间隔为什么能用首末差**：
相邻间隔之和 = 末次 − 首次，除以间隔个数即得均值。
一次聚合就能拿到，不需要逐条两两计算。

## 实现要点

- `observeStatsOf`：COUNT + MIN + MAX，LEFT JOIN 保证无记录时也有行
- `StatusCalculator.computePaged`：接收聚合值，不依赖已加载记录
- `EventStatus.recordCount`：真实总数，区别于 `records.size`
- 流只订阅首屏 20 条；更早的通过 `loadMore` 追加，
  避免每加载一批首屏就重组一次
