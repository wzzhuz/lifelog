package com.zwz.lifelog.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zwz.lifelog.domain.model.EventStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 单事件型小组件：大字显示距今天数，点击直接记一笔。
 *
 * 点击通过 [QuickRecordActivity] 完成 —— 该 Activity 主题透明且记录后立即 finish，
 * 因此用户感知是「点一下就记好了」，不会有界面闪烁。
 *
 * 绑定的 eventId 存在 SharedPreferences 里（而非 Glance 的 DataStore state），
 * 这样能避开 Glance 状态 API 在不同版本间的差异，实现更稳。
 */
class SingleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val eventId = WidgetPrefs.getEventId(context)
        val status = loadOne(context, eventId)

        provideContent {
            val s = status
            if (s == null) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(androidx.compose.ui.graphics.Color(0xFFFFFFFF)))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("未选择事件", style = TextStyle(fontSize = 13.sp))
                    Text("重新添加可绑定", style = TextStyle(fontSize = 10.sp))
                }
            } else {
                val intent = Intent(context, QuickRecordActivity::class.java).apply {
                    putExtra(QuickRecordActivity.EXTRA_EVENT_ID, s.event.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(androidx.compose.ui.graphics.Color(0xFFFFFFFF)))
                        .padding(12.dp)
                        .clickable(actionStartActivity(intent)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.event.emoji, style = TextStyle(fontSize = 20.sp))
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        s.event.name,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    // 吃药这类事件的间隔是几小时，写死「天前」会一直显示 0，
                    // 24 小时内改用小时。
                    val last = s.lastTimestamp
                    val hours = if (last == null) null
                    else (System.currentTimeMillis() - last) / 3_600_000L
                    val useHours = hours != null && hours < 24
                    Text(
                        when {
                            last == null -> "—"
                            useHours -> "$hours"
                            else -> "${s.daysAgo}"
                        },
                        style = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = freshnessColor(s.freshness)
                        )
                    )
                    Text(
                        when {
                            last == null -> "待记录"
                            useHours -> "小时前 · 点击记录"
                            else -> "天前 · 点击记录"
                        },
                        style = TextStyle(fontSize = 10.sp)
                    )
                }
            }
        }
    }

    private suspend fun loadOne(context: Context, eventId: Long): EventStatus? =
        withContext(Dispatchers.IO) {
            if (eventId == 0L) return@withContext null
            runCatching {
                val repo = com.zwz.lifelog.di.ServiceLocator.provideRepository(context)
                repo.load()
                // 只查这一个事件，不再把全部记录读出来再筛选
                repo.statusOfOnce(eventId)
            }.getOrNull()
        }
}

class SingleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleWidget()
}
