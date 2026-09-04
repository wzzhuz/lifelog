package com.zwz.lifelog.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.di.ServiceLocator
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.domain.model.Freshness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小组件公共工具。
 *
 * 注意：Glance 不是完整的 Compose，只有 Glance 提供的 Composable 可用，
 * 这里刻意只用了最基础的 Column/Row/Text，避免用到不支持的 API 导致运行时崩溃。
 */

/**
 * 取「最该做」的几个事件。
 *
 * 走 SQL 聚合（COUNT/MIN/MAX），不读取任何记录行——
 * 小组件每次刷新都会调用，全量加载记录在记录变多后会明显变慢。
 */
internal suspend fun loadTopEvents(context: Context, limit: Int): List<EventStatusLite> =
    withContext(Dispatchers.IO) {
        runCatching {
            val repo = ServiceLocator.provideRepository(context)
            repo.load()
            repo.statusesLiteOnce().take(limit)
        }.getOrDefault(emptyList())
    }

internal fun freshnessColor(f: Freshness): ColorProvider = when (f) {
    Freshness.FRESH -> ColorProvider(Color(0xFF16A34A))
    Freshness.SOON -> ColorProvider(Color(0xFFE08C00))
    Freshness.DUE -> ColorProvider(Color(0xFFE5484D))
    Freshness.NONE -> ColorProvider(Color(0xFF9CA3AF))
}

/**
 * 小组件上的「距今天数」文案。
 *
 * 用 [EventStatusLite.daysAgo]（自然日）而非流逝整天数：
 * 早上 9 点看昨晚 10 点记的事，按自然日才算「昨天」。
 */
internal fun daysText(s: EventStatusLite): String = when {
    s.daysAgo == null -> "未记"
    s.daysAgo == 0 -> "今天"
    else -> "${s.daysAgo} 天"
}
