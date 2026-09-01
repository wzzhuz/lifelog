package com.zwz.lifelog.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 长按拖拽排序的列表容器。
 *
 * 两处关键实现，都是踩过坑后定的：
 *
 * **1. 自定义长按检测，不用官方 `detectDragGesturesAfterLongPress`**
 * 卡片内部有 `clickable`，它在 Main pass 会消费按下事件。
 * 而官方实现用 `requireUnconsumed = true`，外层拖拽检测
 * 因此可能永远收不到按下 → 拖拽看起来能动，但 `onDragEnd` 不触发
 * → **顺序保存不下来**。这里自己实现，用 `requireUnconsumed = false`。
 *
 * **2. 弹簧动画**
 * 位移用 [Animatable] 归位，缩放与透明度用 [animateFloatAsState]，
 * 松手后平滑回落而不是瞬间跳变。
 */
@Composable
fun <T> DragSortLazyColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    /** 返回 false 表示不允许移动到该位置（如跨越分组头）。 */
    canMove: (fromIndex: Int, toIndex: Int) -> Boolean = { _, _ -> true },
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
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

            val animatedOffset = remember { Animatable(0f) }
            LaunchedEffect(isDragging, rawOffset) {
                animatedOffset.animateTo(
                    targetValue = if (isDragging) rawOffset else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }

            val scale by animateFloatAsState(
                targetValue = if (isDragging) 1.03f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isDragging) 0.92f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragAlpha"
            )
            val elevation by animateFloatAsState(
                targetValue = if (isDragging) 8f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragElevation"
            )

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = animatedOffset.value
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        shadowElevation = elevation
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
                                // 取消也要保存：此时列表可能已被拖动过
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
 * 自己实现的长按拖拽检测。
 *
 * 与官方 `detectDragGesturesAfterLongPress` 的唯一实质区别：
 * [awaitFirstDown] 传 `requireUnconsumed = false`，
 * 因此即使内部有 `clickable` 消费了按下事件也能收到。
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressDrag(
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (deltaY: Float) -> Unit
) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            var dragging = false
            var reported = false
            val longPressJob: Job = launch {
                delay(250)
                dragging = true
                onDragStart()
            }

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (change.changedToUpIgnoreConsumed()) {
                        if (dragging) {
                            reported = true
                            onDragEnd()
                        }
                        break
                    }

                    val delta = change.positionChange()
                    if (delta.x != 0f || delta.y != 0f) {
                        if (dragging) {
                            onDrag(delta.y)
                            change.consume()
                        } else {
                            // 长按未成立就移动 → 用户想滚动列表，放弃本次拖拽
                            break
                        }
                    }
                }
            } finally {
                longPressJob.cancel()
                // 循环异常退出时兜底保存，避免已拖动的位置丢失
                if (dragging && !reported) {
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
