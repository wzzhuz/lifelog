package com.zwz.lifelog.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 通知系统刷新小组件。
 * 数据变化后调用，保证桌面显示的天数跟 App 内一致。
 */
object WidgetRefresh {
    fun request(context: Context) {
        runCatching {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)

            val ids1 = manager.getAppWidgetIds(ComponentName(app, ListWidgetReceiver::class.java))
            if (ids1.isNotEmpty()) {
                app.sendBroadcast(Intent(app, ListWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids1)
                })
            }

            val ids2 = manager.getAppWidgetIds(ComponentName(app, SingleWidgetReceiver::class.java))
            if (ids2.isNotEmpty()) {
                app.sendBroadcast(Intent(app, SingleWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids2)
                })
            }
        }
    }
}
