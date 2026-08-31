package com.zwz.lifelog.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zwz.lifelog.MainActivity

/**
 * 列表型小组件：显示最多 5 个事件及距今天数，点击打开应用。
 *
 * 只使用了 Glance 最基础的 Column/Row/Text —— Glance 不是完整 Compose，
 * 用 LazyColumn 或复杂布局会导致运行时崩溃。
 */
class ListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val events = loadTopEvents(context, limit = 5)

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(
                        ColorProvider(
                            androidx.compose.ui.graphics.Color(0xFFF6F7F9),
                            androidx.compose.ui.graphics.Color(0xFF161A1F)
                        )
                    )
                    .padding(12.dp)
            ) {
                if (events.isEmpty()) {
                    Text(
                        "还没有事件，打开应用添加",
                        style = TextStyle(fontSize = 13.sp),
                        modifier = GlanceModifier.padding(4.dp)
                    )
                } else {
                    events.forEach { s ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.event.emoji, style = TextStyle(fontSize = 15.sp))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                s.event.name,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                daysText(s),
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = freshnessColor(s.freshness)
                                )
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        "点击打开应用",
                        style = TextStyle(fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()
}
