package com.zwz.lifelog.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.Freshness

/**
 * 状态色与标签。
 *
 * 从 EventCard.kt 拆出来的原因：详情页、小组件都要用，
 * 而 EventCard 只服务首页列表。放一起会让小组件
 * 被迫依赖一整个列表卡片文件。
 *
 * ⚠️ 使用说明页里有一份同样的色值对照表，
 * 改动这里务必同步更新 `UsageGuideScreen`。
 */
fun freshnessColor(f: Freshness): Color = when (f) {
    Freshness.FRESH -> Color(0xFF16A34A)
    Freshness.SOON -> Color(0xFFE08C00)
    Freshness.DUE -> Color(0xFFE5484D)
    Freshness.NONE -> Color(0xFF9CA3AF)
    // 按需事件：中性灰蓝，刻意不使用任何「紧急」色
    Freshness.IDLE -> Color(0xFF94A3B8)
}

fun freshnessLabel(f: Freshness): String = when (f) {
    Freshness.FRESH -> "新鲜"
    Freshness.SOON -> "快到了"
    Freshness.DUE -> "该做了"
    Freshness.NONE -> "待记录"
    Freshness.IDLE -> "按需"
}

@Composable
fun StatusChip(f: Freshness) {
    val c = freshnessColor(f)
    Surface(
        shape = RoundedCornerShape(50),
        color = c.copy(alpha = 0.14f)
    ) {
        Text(
            freshnessLabel(f),
            style = MaterialTheme.typography.labelSmall,
            color = c,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
