package com.zwz.lifelog.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.di.ServiceLocator
import com.zwz.lifelog.domain.model.EventStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 单事件型小组件：一个大字显示距今天数，点击直接记一笔。
 *
 * 点击走 [RecordActionCallback] 在后台完成记录并自刷新，
 * 不启动任何 Activity，因此没有界面闪烁。
 */
class SingleWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val eventId = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_EVENT_ID]
        }.getOrNull() ?: 0L

        val status = loadOne(context, eventId)

        provideContent {
            val s = status
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(
                        androidx.glance.unit.ColorProvider(
                            androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            androidx.compose.ui.graphics.Color(0xFF181B20)
                        )
                    )
                    .padding(14.dp)
                    .let { if (s != null) it.clickable(actionRunCallback<RecordActionCallback>()) else it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (s == null) {
                    Text("未选择事件", style = TextStyle(fontSize = 13.sp))
                    Text("长按重新添加", style = TextStyle(fontSize = 11.sp))
                } else {
                    Text(s.event.emoji, style = TextStyle(fontSize = 22.sp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        s.event.name,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        s.daysSince?.toString() ?: "—",
                        style = TextStyle(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = freshnessColor(s.freshness)
                        )
                    )
                    Text(
                        if (s.daysSince == null) "待记录" else "天前 · 点击记录",
                        style = TextStyle(fontSize = 11.sp)
                    )
                }
            }
        }
    }

    private suspend fun loadOne(context: Context, eventId: Long): EventStatus? =
        withContext(Dispatchers.IO) {
            if (eventId == 0L) return@withContext null
            runCatching {
                val repo = ServiceLocator.provideRepository(context)
                repo.load()
                val snap = repo.allRaw()
                val ev = snap.events.firstOrNull { it.id == eventId } ?: return@runCatching null
                val recs = snap.records.filter { it.eventId == ev.id }.sortedBy { it.timestamp }
                com.zwz.lifelog.domain.usecase.StatusCalculator.compute(ev, recs)
            }.getOrNull()
        }

    companion object {
        val KEY_EVENT_ID = longPreferencesKey("single_widget_event_id")
    }
}

/**
 * 小组件点击回调：在后台记一笔，然后刷新小组件本身。
 * 注意：ActionCallback 必须是无参构造的 public 类，否则 Glance 反射实例化会失败。
 */
class RecordActionCallback : androidx.glance.action.ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val eventId = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[
                SingleWidget.KEY_EVENT_ID
            ]
        }.getOrNull() ?: 0L

        if (eventId == 0L) return

        var name = ""
        withContext(Dispatchers.IO) {
            runCatching {
                val repo: LifeLogRepository = ServiceLocator.provideRepository(context)
                repo.load()
                name = repo.eventById(eventId)?.name ?: ""
                repo.quickRecord(eventId)
            }
        }

        if (name.isNotBlank()) {
            Toast.makeText(context, "已记录：$name", Toast.LENGTH_SHORT).show()
        }
        runCatching { SingleWidget().update(context, glanceId) }

        // 同步刷新列表型小组件
        WidgetRefresh.request(context)
    }
}

class SingleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleWidget()
}
