package com.zwz.lifelog.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 长按拖拽排序的懒加载列表。
 *
 * 采用长按而非固定手柄（使用者要求先体验此方案，误触率留待实测）。
 * 若实测误触明显，改为在卡片右侧放固定手柄即可，
 * 替换点是 [dragHandle] 的调用处。
 *
 * 实现要点：
 * - 长按 250ms 后才进入拖拽，避免与点击冲突
 * - 被拖起的项 zIndex 提高、轻微放大，视觉上与其余项分离
 * - 拖拽时列表**不自动滚动**到屏幕外（简化实现，事件量通常几十个够用）
 */
@Composable
fun <T> DragSortLazyColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemContent: @Composable (item: T, index: Int, isDragging: Boolean, dragHandle: @Composable () -> Unit) -> Unit
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(items, key = { _, item -> keyOf(item) }) { index, item ->
            val isDragging = index == draggingIndex
            val offsetY = if (isDragging) dragOffset else 0f

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = offsetY
                        if (isDragging) {
                            scaleX = 1.03f
                            scaleY = 1.03f
                            alpha = 0.92f
                        }
                    }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                                onDragEnd()
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y

                                // 越过相邻项高度的一半即交换位置
                                val current = draggingIndex
                                if (current >= 0) {
                                    val target = computeTargetIndex(
                                        listState = listState,
                                        currentIndex = current,
                                        offsetY = dragOffset
                                    )
                                    if (target != current && target in items.indices) {
                                        onReorder(current, target)
                                        draggingIndex = target
                                        dragOffset = 0f
                                    }
                                }
                            }
                        )
                    }
            ) {
                // 函数类型参数不支持命名实参，只能按位置传
                itemContent(item, index, isDragging, {})
            }
        }
    }
}

/**
 * 根据拖拽位移算出目标位置。
 *
 * 取被拖项与其相邻项的高度作为交换阈值的一半，
 * 位移超过即认为拖到了那个位置。
 */
private fun computeTargetIndex(
    listState: LazyListState,
    currentIndex: Int,
    offsetY: Float
): Int {
    if (offsetY == 0f) return currentIndex
    val info: LazyListItemInfo = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == currentIndex } ?: return currentIndex

    val neighborSize = run {
        val next = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentIndex + 1 }
        val prev = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentIndex - 1 }
        (next?.size ?: prev?.size ?: info.size).toFloat()
    }
    val threshold = neighborSize * 0.5f

    return when {
        offsetY > threshold -> currentIndex + 1
        offsetY < -threshold -> currentIndex - 1
        else -> currentIndex
    }
}
