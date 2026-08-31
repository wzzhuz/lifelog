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
