package com.raycoll.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raycoll_settings")

data class UserPreferences(
    val themePref: String = "system",
    val useSystemColors: Boolean = true,
    val uiLanguage: String = "pl",
    val ttsRate: Float = 1.0f,
    val backgroundPlayback: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_pref")
        val SYSTEM_COLORS = booleanPreferencesKey("use_system_colors")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themePref = prefs[Keys.THEME] ?: "system",
            useSystemColors = prefs[Keys.SYSTEM_COLORS] ?: true,
            uiLanguage = prefs[Keys.UI_LANGUAGE] ?: "pl",
            ttsRate = (prefs[Keys.TTS_RATE] ?: 1.0f).coerceIn(0.5f, 2.0f),
            backgroundPlayback = prefs[Keys.BACKGROUND_PLAYBACK] ?: false,
        )
    }

    suspend fun setThemePref(value: String) {
        context.dataStore.edit { it[Keys.THEME] = value }
    }

    suspend fun setUseSystemColors(value: Boolean) {
        context.dataStore.edit { it[Keys.SYSTEM_COLORS] = value }
    }

    suspend fun setUiLanguage(value: String) {
        context.dataStore.edit { it[Keys.UI_LANGUAGE] = value }
    }

    suspend fun setTtsRate(value: Float) {
        context.dataStore.edit { it[Keys.TTS_RATE] = value.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setBackgroundPlayback(value: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_PLAYBACK] = value }
    }
}
