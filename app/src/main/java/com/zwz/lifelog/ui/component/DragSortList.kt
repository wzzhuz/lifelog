package com.zwz.lifelog.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 长按拖拽排序的列表容器。
 *
 * 两处关键实现，都是踩过坑后定的：
 *
 * **1. 自定义长按检测，而非 [detectDragGesturesAfterLongPress]**
 * 卡片内部有 `clickable`，它在 Main pass 会消费按下事件。
 * 而 `detectDragGesturesAfterLongPress` 用 `requireUnconsumed = true`，
 * 外层的拖拽检测因此可能永远收不到按下 → 拖拽看起来能动，
 * 但 `onDragEnd` 不触发 → **顺序保存不下来**。
 * 这里自己实现，用 Main pass + 不检查消费状态，保证一定能收到。
 *
 * **2. `animateItemPlacement`**
 * 让重新排序时其他卡片平滑让位，而不是瞬间跳到新位置。
 */
@Composable
fun <T> DragSortLazyColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    /** 返回 false 表示不允许移动到该位置（如分组头）。 */
    canMove: (fromIndex: Int, toIndex: Int) -> Boolean = { _, _ -> true },
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical =
        androidx.compose.foundation.layout.Arrangement.Top,
    itemContent: @Composable (item: T, index: Int, isDragging: Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var rawOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(items, key = { _, item -> keyOf(item) }) { index, item ->
            val isDragging = index == draggingIndex

            // 位移用弹簧动画，松手后平滑归位而不是瞬间跳回
            val animatedOffset = remember { Animatable(0f) }
            LaunchedEffect(isDragging, rawOffset) {
                animatedOffset.animateTo(
                    if (isDragging) rawOffset else 0f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }

            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isDragging) 1.04f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragScale"
            )
            val alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isDragging) 0.9f else 1f,
                label = "dragAlpha"
            )

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .animateItemPlacement(spring(stiffness = Spring.StiffnessMediumLow))
                    .graphicsLayer {
                        translationY = animatedOffset.value
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .pointerInput(index) {
                        detectLongPressDrag(
                            onDragStart = {
                                draggingIndex = index
                                rawOffset = 0f
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                rawOffset = 0f
                                onDragEnd()
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                rawOffset = 0f
                                // 取消也要保存：此时列表可能已经被拖过
                                onDragEnd()
                            },
                            onDrag = { deltaY ->
                                rawOffset += deltaY
                                val current = draggingIndex
                                if (current >= 0) {
                                    val target = computeTargetIndex(
                                        listState = listState,
                                        currentIndex = current,
                                        offsetY = rawOffset
                                    )
                                    if (target != current &&
                                        target in items.indices &&
                                        canMove(current, target)
                                    ) {
                                        onReorder(current, target)
                                        draggingIndex = target
                                        rawOffset = 0f
                                    }
                                }
                            }
                        )
                    }
            ) {
                itemContent(item, index, isDragging)
            }
        }
    }
}

/**
 * 自己实现长按拖拽。
 *
 * 与官方 `detectDragGesturesAfterLongPress` 的区别：
 * 用 `awaitFirstDown(requireUnconsumed = false)`，
 * 因此即使内部有 `clickable` 消费了按下事件也能正常工作。
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressDrag(
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (deltaY: Float) -> Unit
) {
    coroutineScope {
        androidx.compose.ui.input.pointer.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            var longPressJob: kotlinx.coroutines.Job? = null
            var dragging = false

            try {
                // 长按 250ms 进入拖拽
                longPressJob = launch {
                    delay(250)
                    dragging = true
                    onDragStart()
                }

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change: PointerInputChange? = event.changes.firstOrNull { it.id == down.id }
                        ?: break

                    when {
                        change.pressed.not() -> {
                            if (dragging) onDragEnd() else longPressJob.cancel()
                            break
                        }
                        change.positionChange().let { it.x != 0f || it.y != 0f } -> {
                            if (dragging) {
                                onDrag(change.positionChange().y)
                                change.consume()
                            } else {
                                // 长按未成立就移动，视为滚动，放弃
                                longPressJob.cancel()
                                break
                            }
                        }
                    }
                }
            } finally {
                longPressJob?.cancel()
                if (dragging) {
                    // 若循环因异常退出，兜底收尾
                    runCatching { onDragCancel() }
                }
            }
        }
    }
}

private fun computeTargetIndex(
    listState: LazyListState,
    currentIndex: Int,
    offsetY: Float
): Int {
    if (offsetY == 0f) return currentIndex
    val info: LazyListItemInfo = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == currentIndex } ?: return currentIndex

    val neighborSize = run {
        val next = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex + 1 }
        val prev = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex - 1 }
        (next?.size ?: prev?.size ?: info.size).toFloat()
    }
    val threshold = neighborSize * 0.5f

    return when {
        offsetY > threshold -> currentIndex + 1
        offsetY < -threshold -> currentIndex - 1
        else -> currentIndex
    }
}
