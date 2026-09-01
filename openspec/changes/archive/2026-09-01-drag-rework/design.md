# 技术设计：拖拽改造

## 上一版为什么难用

```kotlin
onDrag = { deltaY ->
    rawOffset += deltaY
    val target = computeTargetIndex(...)   // 只可能返回 current ± 1
    if (target != current) {
        onReorder(current, target)
        draggingIndex = target
        rawOffset = 0f                     // ← 归零
    }
}
```

两个问题叠加：最多移动一格 + 每次交换后位移归零。
想从第 10 位移到第 2 位要连续拖 8 次。

## 新方案

### 1. 累积总位移

记录按下点，用「当前 − 起点」作为总位移，**全程不归零**。

### 2. 松手才落位

拖拽期间只改 `graphicsLayer.translationY`（视觉位移），
**不动数据列表**。松手时执行一次 `move(from, to)`。

好处：拖拽过程零数据变更、零重组。

### 3. 一次跨越多项

不能用「位移 ÷ 固定高度」——分组模式下有分组头（28dp）
和卡片（72~112dp），高度不一致。

必须按 `layoutInfo` 逐项累加**实际尺寸**：

```
向下：依次累加下方各项高度，直到累计超过总位移
向上：依次累加上方各项高度
```

遇到 `canMove` 返回 false（分组头）即停止，实现组内限制。

### 4. 让位

被跨过的项按身位平滑让开：
- 向下拖时，位于 (from, to] 的项上移一个身位
- 向上拖时，位于 [to, from) 的项下移一个身位

### 5. 视觉反馈

放大 1.04 + 透明度 0.92 + 阴影 12dp，明确表达「已拿起」。

## 一处已知取舍

`animateItemPlacement` 在新版 Compose Foundation 中已被移除，
替代品是 `animateItem`。本次未采用，让位动画用
graphicsLayer 位移手动实现——效果接近，但不如官方 API 平滑。
