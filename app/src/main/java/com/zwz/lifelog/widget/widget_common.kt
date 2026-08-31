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
import com.zwz.lifelog.domain.model.EventStatus
import com.zwz.lifelog.domain.model.Freshness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小组件公共工具。
 *
 * 注意：Glance 不是完整的 Compose，只有 Glance 提供的 Composable 可用，
 * 这里刻意只用了最基础的 Column/Row/Text，避免用到不支持的 API 导致运行时崩溃。
 */

internal suspend fun loadTopEvents(context: Context, limit: Int): List<EventStatus> =
    withContext(Dispatchers.IO) {
        runCatching {
            val repo = ServiceLocator.provideRepository(context)
            repo.load()
            val snap = repo.allRaw()
            val byEvent = snap.records.groupBy { it.eventId }
            snap.events
                .filter { !it.isArchived }
                .map { ev ->
                    com.zwz.lifelog.domain.usecase.StatusCalculator.compute(
                        ev, (byEvent[ev.id] ?: emptyList()).sortedBy { it.timestamp }
                    )
                }
                .sortedWith(
                    compareByDescending<EventStatus> { it.event.isPinned }
                        .thenByDescending { it.ratio }
                )
                .take(limit)
        }.getOrDefault(emptyList())
    }

internal fun freshnessColor(f: Freshness): ColorProvider = when (f) {
    Freshness.FRESH -> ColorProvider(Color(0xFF16A34A))
    Freshness.SOON -> ColorProvider(Color(0xFFE08C00))
    Freshness.DUE -> ColorProvider(Color(0xFFE5484D))
    Freshness.NONE -> ColorProvider(Color(0xFF9CA3AF))
}

internal fun daysText(s: EventStatus): String = when {
    s.daysSince == null -> "未记"
    s.daysSince == 0 -> "今天"
    else -> "${s.daysSince} 天"
}
