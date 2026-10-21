package com.doublesail.scheduleboard.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.boardDataStore by preferencesDataStore(
    name = "board_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.boardDataStore

    val settings = store.data
        .catch { error ->
            if (error is IOException) {
                Log.e("BoardSettings", "Cannot read settings; using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { values ->
            BoardSettings(
                keepScreenOn = values[KeepScreenOn] ?: true,
                immersive = values[Immersive] ?: true,
                dynamicColor = values[DynamicColor] ?: true,
                autoStart = values[AutoStart] ?: true,
                themeMode = ThemeMode.entries.firstOrNull { it.name == values[Theme] }
                    ?: ThemeMode.SYSTEM,
            )
        }

    suspend fun setKeepScreenOn(value: Boolean) { store.edit { it[KeepScreenOn] = value } }
    suspend fun setImmersive(value: Boolean) { store.edit { it[Immersive] = value } }
    suspend fun setDynamicColor(value: Boolean) { store.edit { it[DynamicColor] = value } }
    suspend fun setAutoStart(value: Boolean) { store.edit { it[AutoStart] = value } }
    suspend fun setThemeMode(value: ThemeMode) { store.edit { it[Theme] = value.name } }

    private companion object {
        val KeepScreenOn = booleanPreferencesKey("keep_screen_on")
        val Immersive = booleanPreferencesKey("immersive")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val AutoStart = booleanPreferencesKey("auto_start")
        val Theme = stringPreferencesKey("theme_mode")
    }
}
