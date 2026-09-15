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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness

/** 卡片展示密度。 */
enum class CardDensity {
    /**
     * 紧凑：父事件 58dp / 子事件 48dp。
     * 进度条压成贴底细线，状态用圆点。一屏可放 10 个以上。
     */
    COMPACT,

    /** 舒适：父事件 96dp / 子事件 84dp。信息完整，状态用文字 chip。 */
    COMFORT
}

@Composable
fun EventCard(
    status: EventStatusLite,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier,
    density: CardDensity = CardDensity.COMPACT,
    /**
     * 渲染「距今」文案的时刻。
     *
     * 副标题是**渲染时**算出来的，而列表只在数据库变化时才发射新值。
     * 不传这个的话，App 停在首页不动，「8 小时前」会一直停在打开那一刻。
     * 由 ListViewModel 的分钟级 tick 提供，整屏共用一个值。
     */
    now: Long = System.currentTimeMillis()
) {
    val barColor by animateColorAsState(
        targetValue = freshnessColor(status.freshness),
        label = "bar"
    )

    if (density == CardDensity.COMPACT) {
        CompactCard(
            status = status,
            barColor = barColor,
            now = now,
            onClick = onClick,
            onQuickRecord = onQuickRecord,
            modifier = modifier
        )
    } else {
        ComfortCard(
            status = status,
            barColor = barColor,
            now = now,
            onClick = onClick,
            onQuickRecord = onQuickRecord,
            modifier = modifier
        )
    }
}

/**
 * 紧凑卡片。
 *
 * 关键取舍：进度条从独立一行改为**贴底 2dp 细线**，
 * 不再占用纵向空间。
 *
 * 高度：父事件 58dp，子事件 48dp。
 * 内容是 emoji + 两行文字（约 38dp），此前写死 72dp 会让
 * 文字只占一半、上下大片留白；打钩按钮 36dp 又与文字区等高，
 * 进一步放大空旷感。现在按钮收到 32dp，高度贴合内容。
 */
@Composable
private fun CompactCard(
    status: EventStatusLite,
    barColor: androidx.compose.ui.graphics.Color,
    now: Long,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isChild = status.event.parentId != null
    val cardHeight = if (isChild) 48.dp else 58.dp
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 子事件缩进 + 左侧引导线：光靠缩进在滚动时不够醒目，
            // 加一条竖线把「属于上面那个疗程」这件事画出来
            .then(
                if (isChild) Modifier.padding(start = 26.dp).drawBehind {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Box {
            Row(modifier = Modifier.height(cardHeight)) {
                // 色条跟随卡片实际高度。写死高度曾导致色条比卡片矮一截，
                // 下方一段没颜色。
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(cardHeight)
                        .background(barColor)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 子事件左侧的引导线，颜色与色条同源但弱化，
                    // 视觉上把子卡片挂到父卡片下面
                    if (isChild) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(cardHeight - 20.dp)
                                .background(barColor.copy(alpha = 0.35f))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(status.event.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                displayNameOf(status),
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
                            subtitleOf(status, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!status.event.isCourse) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onQuickRecord,
                            modifier = Modifier.size(32.dp),
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
    now: Long,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isChild = status.event.parentId != null
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isChild) Modifier.padding(start = 20.dp) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row {
            // 高度跟随内容，不再写死；子事件矮一档
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (isChild) 84.dp else 96.dp)
                    .background(barColor)
            )
            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.event.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        displayNameOf(status),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(status.freshness)
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    subtitleOf(status, now),
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
                    if (!status.event.isCourse) {
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

/**
 * 卡片副标题。
 *
 * @param now 渲染时刻，来自列表页的分钟级 tick。
 *            不传的话「8 小时前」会停在页面打开那一刻不再走动。
 */
@Composable
private fun subtitleOf(s: EventStatusLite, now: Long): String {
    val fmt = com.zwz.lifelog.util.TimeFormatter
    val parts = mutableListOf<String>()

    // 频次型（「消炎药」）显示「今日 1/3」，疗程显示子事件汇总的「今日 2/5」
    val progress = s.todayProgress
        ?: com.zwz.lifelog.domain.usecase.StatusCalculator.courseProgress(s)
    if (progress != null) parts.add(progress)

    if (s.lastTimestamp == null) {
        // 疗程没有自己的记录，别提示「记一笔」
        if (s.parentName != null) parts.add(s.parentName)
        return if (parts.isEmpty()) {
            if (s.event.isCourse) "还没有子事件" else "点击右侧 ✓ 记一笔"
        } else parts.joinToString(" · ")
    }

    // 24 小时内显示小时：吃药这类间隔几小时的事件，写「今天」等于没说
    parts.add("上次 ${fmt.agoUnit(s.lastTimestamp, now)}")
    if (s.avgGapMillis != null && s.event.timesPerDay == null) {
        parts.add("平均 ${fmt.duration(s.avgGapMillis)}")
    }
    if (s.recordCount >= 2 && s.predictedNextMillis != null) {
        val next = s.predictedNextMillis
        // 基准不足 2 天的事件（吃药、测血糖），只给日期等于没给，得带上时分
        parts.add("预计 ${if (s.baselineDays < 2) fmt.short(next, now = now) else fmt.dateOnly(next)}")
    }
    // 归属放在最后且弱化：父卡片可能已滚出屏幕，这里要能独立回答「属于谁」
    if (s.parentName != null) parts.add(s.parentName)
    return parts.joinToString(" · ")
}

/**
 * 列表里显示的名字。
 *
 * 子事件**不再**带「疗程 · 」前缀。前缀与缩进同时存在既重复又易被截断，
 * 而缩进已经表达了层级；归属信息改由 [subtitleOf] 在副标题末尾承载，
 * 这样主名保持短药名，滚动到列表中部也仍能确认属于哪次疗程。
 */
private fun displayNameOf(s: EventStatusLite): String = s.event.name
