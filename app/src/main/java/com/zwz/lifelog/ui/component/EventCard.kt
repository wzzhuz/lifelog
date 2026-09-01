package com.zwz.lifelog.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness

/** 卡片展示密度。 */
enum class CardDensity {
    /** 紧凑：约 76dp，一屏 8~9 个。进度条压成贴底细线，状态用圆点。 */
    COMPACT,

    /** 舒适：约 112dp，信息完整，状态用文字 chip。 */
    COMFORT
}

@Composable
fun EventCard(
    status: EventStatusLite,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier,
    density: CardDensity = CardDensity.COMPACT,
    /** 拖拽手柄（分组模式等场景下可置空表示不支持拖拽） */
    dragHandle: (@Composable () -> Unit)? = null
) {
    val barColor by animateColorAsState(
        targetValue = freshnessColor(status.freshness),
        label = "bar"
    )

    if (density == CardDensity.COMPACT) {
        CompactCard(
            status = status,
            barColor = barColor,
            onClick = onClick,
            onQuickRecord = onQuickRecord,
            modifier = modifier,
            dragHandle = dragHandle
        )
    } else {
        ComfortCard(
            status = status,
            barColor = barColor,
            onClick = onClick,
            onQuickRecord = onQuickRecord,
            modifier = modifier,
            dragHandle = dragHandle
        )
    }
}

/**
 * 紧凑卡片。
 *
 * 关键取舍：进度条从独立一行改为**贴底 2dp 细线**，
 * 不再占用纵向空间——这是高度能从 112dp 降到 76dp 的主因。
 */
@Composable
private fun CompactCard(
    status: EventStatusLite,
    barColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Box {
            Row(modifier = Modifier.height(72.dp)) {
                // 色条跟随卡片实际高度，不再写死 84dp。
                // 写死曾导致色条比卡片矮 30~40dp，下方一截没颜色。
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(72.dp)
                        .background(barColor)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status.event.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                status.event.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusDot(status.freshness)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitleOf(status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (dragHandle != null) {
                        dragHandle()
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onQuickRecord,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "记一笔",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            // 贴底进度条：不占纵向空间
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(status.ratio.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(barColor.copy(alpha = 0.75f))
            )
        }
    }
}

/** 舒适卡片：保持原有布局，仅修好色条高度。 */
@Composable
private fun ComfortCard(
    status: EventStatusLite,
    barColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row {
            // fillMaxHeight 跟随内容，不再写死
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(112.dp)
                    .background(barColor)
            )
            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.event.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        status.event.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(status.freshness)
                    if (dragHandle != null) dragHandle()
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    subtitleOf(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(status.ratio.coerceIn(0f, 1.5f) / 1.5f)
                                .height(5.dp)
                                .background(barColor)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = onQuickRecord,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "记一笔",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 紧凑模式的状态圆点：颜色即状态，配合说明页图例不至于看不懂。 */
@Composable
private fun StatusDot(f: Freshness) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(freshnessColor(f))
    )
}

@Composable
private fun subtitleOf(s: EventStatusLite): String {
    if (s.lastTimestamp == null) return "点击右侧 ✓ 记一笔"
    val parts = mutableListOf<String>()
    parts.add("上次 ${com.zwz.lifelog.util.TimeFormatter.agoWithDate(s.lastTimestamp)}")
    if (s.avgGapMillis != null) {
        parts.add("平均 ${com.zwz.lifelog.util.TimeFormatter.duration(s.avgGapMillis)}")
    }
    if (s.recordCount >= 2 && s.predictedNextMillis != null) {
        parts.add("预计 ${com.zwz.lifelog.util.TimeFormatter.dateOnly(s.predictedNextMillis)}")
    }
    return parts.joinToString(" · ")
}
