package com.zwz.lifelog.widget

import android.content.Context

/**
 * 单事件型小组件「当前绑定到哪个事件」的存储。
 *
 * 用 SharedPreferences 而不是 Glance 的 DataStore state，原因：
 * Glance 的状态 API（PreferencesGlanceStateDefinition / updateAppWidgetState）
 * 在不同版本间签名有差异，且 lambda 返回值类型要求严格，容易踩坑。
 * 这里只需要存一个 Long，SharedPreferences 完全够用且行为确定。
 */
object WidgetPrefs {

    private const val FILE = "lifelog_widget_prefs"
    private const val KEY_EVENT_ID = "single_widget_event_id"

    fun getEventId(context: Context): Long {
        return context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getLong(KEY_EVENT_ID, 0L)
    }

    fun setEventId(context: Context, eventId: Long) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_EVENT_ID, eventId)
            .apply()
    }
}
