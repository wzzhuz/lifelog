package com.zwz.lifelog.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePrefs {
    private val MODE = stringPreferencesKey("theme_mode")
    private val DYNAMIC = booleanPreferencesKey("dynamic_color")

    fun mode(context: Context): Flow<String> =
        context.dataStore.data.map { it[MODE] ?: "auto" }

    suspend fun setMode(context: Context, mode: String) {
        context.dataStore.edit { it[MODE] = mode }
    }

    fun dynamicColor(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[DYNAMIC] ?: true }

    suspend fun setDynamicColor(context: Context, on: Boolean) {
        context.dataStore.edit { it[DYNAMIC] = on }
    }
}

// ---------------------------------------------------------------------------
// 首页布局模式
// ---------------------------------------------------------------------------

/**
 * 首页列表的展示模式。
 *
 * | 模式 | 一屏数量 | 适用 |
 * |---|---|---|
 * | COMPACT | 8~9 | 日常，事件 20 个以内 |
 * | COMFORT | 5~6 | 信息最完整 |
 * | GROUPED | 7~8 | 事件多，按分类找 |
 */
enum class HomeLayoutMode {
    COMPACT,   // 紧凑列表（默认）
    COMFORT,   // 舒适列表
    GROUPED    // 按分类分组，吸顶头 + 可折叠
}

private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(name = "home_layout")

object HomeLayoutPrefs {

    private val MODE = stringPreferencesKey("layout_mode")

    /** 折叠状态：存分类名集合。分组模式专用。 */
    private val COLLAPSED = stringPreferencesKey("collapsed_tags")

    fun modeFlow(context: Context): Flow<HomeLayoutMode> =
        context.homeDataStore.data.map {
            runCatching { HomeLayoutMode.valueOf(it[MODE] ?: "") }
                .getOrDefault(HomeLayoutMode.COMPACT)
        }

    suspend fun setMode(context: Context, mode: HomeLayoutMode) {
        context.homeDataStore.edit { it[MODE] = mode.name }
    }

    fun collapsedFlow(context: Context): Flow<Set<String>> =
        context.homeDataStore.data.map {
            (it[COLLAPSED] ?: "").split('|').filter { s -> s.isNotBlank() }.toSet()
        }

    suspend fun toggleCollapsed(context: Context, tag: String) {
        context.homeDataStore.edit { prefs ->
            val cur = (prefs[COLLAPSED] ?: "").split('|').filter { it.isNotBlank() }.toMutableSet()
            if (!cur.add(tag)) cur.remove(tag)
            prefs[COLLAPSED] = cur.joinToString("|")
        }
    }
}
