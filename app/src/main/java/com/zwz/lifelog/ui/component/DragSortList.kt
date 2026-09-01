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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 长按选中 → 自由移动 → 松手落位 的拖拽排序列表。
 *
 * ## 与上一版的关键差异
 *
 * 上一版是「边拖边交换、每次只挪一格」：
 * 位移一旦超过半格就立刻 `move(±1)` 并把位移归零，
 * 想从第 10 位移到第 2 位要连续拖 8 次。
 *
 * 这一版改为：
 * - **累积总位移**：记录按下点，用「当前 − 起点」作为总位移，不归零
 * - **松手才落位**：拖拽期间只改视觉位移，不动数据列表，
 *   松手时执行一次 `move(from, to)`
 * - **一次跨越多项**：按总位移和实际项尺寸算出目标下标
 *
 * 拖拽期间零数据变更、零重组，比边拖边改列表流畅。
 *
 * ## 为什么不用官方 detectDragGesturesAfterLongPress
 *
 * 卡片内部有 `clickable`，它在 Main pass 会消费按下事件。
 * 官方实现用 `requireUnconsumed = true`，外层因此收不到按下
 * → `onDragEnd` 不触发 → **顺序保存不下来**。
 * 这里自己实现，用 `requireUnconsumed = false`。
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

    /** 正在被拖的下标，-1 表示无。 */
    var draggingIndex by remember { mutableIntStateOf(-1) }
    /** 相对按下点的累积总位移，拖拽全程不归零。 */
    var totalDrag by remember { mutableFloatStateOf(0f) }
    /** 松手时实际要落到的位置，用于计算预览位移。 */
    var targetIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(items, key = { _, item -> keyOf(item) }) { index, item ->
            val isDragging = index == draggingIndex

            // 被拖项跟随手指；松手后由弹簧动画平滑归位到目标位置
            val visualOffset = remember { Animatable(0f) }
            val targetOffset = if (isDragging) {
                totalDrag
            } else {
                // 非拖拽项：若自己被目标位置跨过，让出位置
                if (targetIndex >= 0 && draggingIndex >= 0) {
                    shiftFor(index, draggingIndex, targetIndex, listState)
                } else 0f
            }

            androidx.compose.runtime.LaunchedEffect(targetOffset, isDragging) {
                if (isDragging) {
                    // 拖拽中：直接跟手，不要动画（否则有延迟感）
                    visualOffset.snapTo(targetOffset)
                } else {
                    visualOffset.animateTo(
                        targetOffset,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }

            val scale by animateFloatAsState(
                targetValue = if (isDragging) 1.04f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isDragging) 0.92f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragAlpha"
            )
            val elevation by animateFloatAsState(
                targetValue = if (isDragging) 12f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dragElevation"
            )

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = visualOffset.value
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        shadowElevation = elevation
                    }
                    .pointerInput(index) {
                        detectLongPressDrag(
                            onDragStart = {
                                draggingIndex = index
                                targetIndex = index
                                totalDrag = 0f
                            },
                            onDrag = { deltaY ->
                                totalDrag += deltaY
                                val current = draggingIndex
                                if (current >= 0) {
                                    val t = resolveTargetIndex(
                                        listState = listState,
                                        fromIndex = current,
                                        totalOffsetY = totalDrag,
                                        canMove = canMove,
                                        itemCount = items.size
                                    )
                                    targetIndex = t
                                }
                            },
                            onDragEnd = {
                                val from = draggingIndex
                                val to = targetIndex
                                draggingIndex = -1
                                targetIndex = -1
                                totalDrag = 0f
                                // 松手才真正改数据，一次到位
                                if (from >= 0 && to >= 0 && from != to && to in items.indices) {
                                    onReorder(from, to)
                                }
                                onDragEnd()
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                targetIndex = -1
                                totalDrag = 0f
                                onDragEnd()
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
 * 计算被拖项跨过的其他项应让出多少位移。
 *
 * 被拖项从 [from] 移到 [to] 时：
 * - 位于 (from, to] 之间的项（向下拖）要上移一个身位
 * - 位于 [to, from) 之间的项（向上拖）要下移一个身位
 */
private fun shiftFor(
    index: Int,
    from: Int,
    to: Int,
    listState: LazyListState
): Float {
    if (index == from) return 0f
    val size = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == index }?.size?.toFloat()
        ?: listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == from }?.size?.toFloat()
        ?: return 0f

    return when {
        to > from && index in (from + 1)..to -> -size
        to < from && index in to until from -> size
        else -> 0f
    }
}

/**
 * 根据累积位移算出目标下标。
 *
 * **不能用「位移 ÷ 固定高度」**：分组模式下有分组头（28dp）
 * 和卡片（72~112dp），项高度不一致。
 * 必须按 layoutInfo 里每项的实际尺寸**逐个累加**。
 */
private fun resolveTargetIndex(
    listState: LazyListState,
    fromIndex: Int,
    totalOffsetY: Float,
    canMove: (fromIndex: Int, toIndex: Int) -> Boolean,
    itemCount: Int
): Int {
    val info = listState.layoutInfo.visibleItemsInfo
    val self = info.firstOrNull { it.index == fromIndex } ?: return fromIndex

    var target = fromIndex
    var accumulated = 0f

    if (totalOffsetY > 0) {
        // 向下：依次累加下方各项高度
        for (k in fromIndex + 1 until itemCount) {
            val next = info.firstOrNull { it.index == k }
            val step = next?.size?.toFloat() ?: self.size.toFloat()
            if (accumulated + step / 2 > totalOffsetY) break
            accumulated += step
            if (!canMove(fromIndex, k)) break
            target = k
        }
    } else if (totalOffsetY < 0) {
        // 向上：依次累加上方各项高度
        for (k in fromIndex - 1 downTo 0) {
            val prev = info.firstOrNull { it.index == k }
            val step = prev?.size?.toFloat() ?: self.size.toFloat()
            if (accumulated - step / 2 < totalOffsetY) break
            accumulated -= step
            if (!canMove(fromIndex, k)) break
            target = k
        }
    }
    return target
}

/**
 * 自己实现的长按拖拽检测。
 *
 * 与官方 `detectDragGesturesAfterLongPress` 的实质区别：
 * [awaitFirstDown] 传 `requireUnconsumed = false`，
 * 因此即使内部有 `clickable` 消费了按下事件也能收到。
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressDrag(
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var origin: Offset? = null
            var dragging = false
            var reported = false

            val longPressJob: Job = launch {
                delay(250)
                dragging = true
                origin = down.position
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
                            // 长按未成立就移动 → 用户想滚动列表，放弃
                            break
                        }
                    }
                }
            } finally {
                longPressJob.cancel()
                if (dragging && !reported) {
                    runCatching { onDragCancel() }
                }
            }
        }
    }
}
