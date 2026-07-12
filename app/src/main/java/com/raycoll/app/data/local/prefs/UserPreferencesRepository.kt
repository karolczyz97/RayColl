package com.raycoll.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raycoll_preferences")

data class UserPreferences(
    val themePref: String = "system",
    val useSystemColors: Boolean = true,
    val uiLanguage: String = "",
    val ttsRate: Float = 1.0f,
    val backgroundPlayback: Boolean = false,
    val lastSeenBuild: String? = null,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_PREF = stringPreferencesKey("theme_pref")
        val USE_SYSTEM_COLORS = booleanPreferencesKey("use_system_colors")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val LAST_SEEN_BUILD = stringPreferencesKey("last_seen_build")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themePref = prefs[Keys.THEME_PREF] ?: "system",
            useSystemColors = prefs[Keys.USE_SYSTEM_COLORS] ?: true,
            uiLanguage = prefs[Keys.UI_LANGUAGE] ?: "",
            ttsRate = (prefs[Keys.TTS_RATE] ?: 1.0f).coerceIn(0.5f, 2.0f),
            backgroundPlayback = prefs[Keys.BACKGROUND_PLAYBACK] ?: false,
            lastSeenBuild = prefs[Keys.LAST_SEEN_BUILD],
        )
    }

    suspend fun setThemePref(value: String) { write(Keys.THEME_PREF, value) }
    suspend fun setUseSystemColors(value: Boolean) { write(Keys.USE_SYSTEM_COLORS, value) }
    suspend fun setUiLanguage(value: String) { write(Keys.UI_LANGUAGE, value) }
    suspend fun setTtsRate(value: Float) { write(Keys.TTS_RATE, value.coerceIn(0.5f, 2.0f)) }
    suspend fun setBackgroundPlayback(value: Boolean) { write(Keys.BACKGROUND_PLAYBACK, value) }
    suspend fun setLastSeenBuild(value: String) { write(Keys.LAST_SEEN_BUILD, value) }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }
}
