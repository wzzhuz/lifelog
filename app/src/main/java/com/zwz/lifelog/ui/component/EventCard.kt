package com.zwz.lifelog.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Freshness
import com.zwz.lifelog.util.TimeFormatter

@Composable
fun freshnessColor(f: Freshness): Color = when (f) {
    Freshness.FRESH -> Color(0xFF16A34A)
    Freshness.SOON -> Color(0xFFE08C00)
    Freshness.DUE -> Color(0xFFE5484D)
    Freshness.NONE -> MaterialTheme.colorScheme.outline
}

@Composable
fun freshnessLabel(f: Freshness): String = when (f) {
    Freshness.FRESH -> "还早"
    Freshness.SOON -> "快到了"
    Freshness.DUE -> "该做了"
    Freshness.NONE -> "待记录"
}

@Composable
fun EventCard(
    status: EventStatus,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor by animateColorAsState(targetValue = freshnessColor(status.freshness), label = "bar")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(start = 0.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(84.dp)
                    .background(barColor)
            )
            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.event.emoji, style = MaterialTheme.typography.titleLarge)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Text(
                        status.event.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                    StatusChip(status.freshness)
                }

                androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))

                Text(
                    subtitleOf(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))

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
                                .fillMaxWidth((status.ratio.coerceIn(0f, 1.5f) / 1.5f))
                                .height(5.dp)
                                .background(barColor)
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = onQuickRecord,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "记一笔", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .width(62.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (status.daysSince == null) {
                    Text("—", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline)
                } else {
                    Text(
                        "${status.daysSince}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("天前", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(f: Freshness) {
    val c = freshnessColor(f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(freshnessLabel(f), style = MaterialTheme.typography.labelMedium, color = c)
    }
}

@Composable
private fun subtitleOf(s: EventStatus): String {
    if (s.lastTimestamp == null) return "添加后点右侧 ✓ 记一笔"
    val parts = mutableListOf<String>()
    parts.add("上次 ${TimeFormatter.agoWithDate(s.lastTimestamp)}")
    if (s.avgGapMillis != null) parts.add("平均 ${TimeFormatter.duration(s.avgGapMillis)}")
    if (s.records.size >= 2 && s.predictedNextMillis != null) {
        parts.add("预计 ${TimeFormatter.dateOnly(s.predictedNextMillis)}")
    }
    return parts.joinToString(" · ")
}
