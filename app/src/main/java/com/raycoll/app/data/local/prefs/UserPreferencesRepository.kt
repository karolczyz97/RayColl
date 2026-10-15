package com.raycoll.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raycoll.app.domain.model.KeyedEnum
import com.raycoll.app.domain.model.TtsRate
import com.raycoll.app.domain.model.keyedEnumOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// uszkodzony plik ustawień = domyślne ustawienia, a nie wyjątek na każdym odczycie
internal val settingsCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "raycoll_settings",
    corruptionHandler = settingsCorruptionHandler
)

enum class ThemePref(override val key: String) : KeyedEnum {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemePref = keyedEnumOrNull(entries, key) ?: SYSTEM
    }
}

/** Ustawienia urządzenia (nie synchronizowane). Język interfejsu trzyma system (per-app locale). */
data class UserPreferences(
    val theme: ThemePref = ThemePref.SYSTEM,
    val useSystemColors: Boolean = true,
    val ttsRate: Float = TtsRate.DEFAULT,
    val backgroundPlayback: Boolean = true,
    val expertMode: Boolean = false
)

@Singleton
class UserPreferencesRepository internal constructor(private val dataStore: DataStore<Preferences>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    private object Keys {
        val THEME = stringPreferencesKey("theme_pref")
        val SYSTEM_COLORS = booleanPreferencesKey("use_system_colors")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val EXPERT_MODE = booleanPreferencesKey("expert_mode")
    }

    private val defaults = UserPreferences()

    val preferences: Flow<UserPreferences> = dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { prefs ->
        UserPreferences(
            theme = ThemePref.fromKey(prefs[Keys.THEME]),
            useSystemColors = prefs[Keys.SYSTEM_COLORS] ?: defaults.useSystemColors,
            ttsRate = TtsRate.clamp(prefs[Keys.TTS_RATE] ?: defaults.ttsRate),
            backgroundPlayback = prefs[Keys.BACKGROUND_PLAYBACK] ?: defaults.backgroundPlayback,
            expertMode = prefs[Keys.EXPERT_MODE] ?: defaults.expertMode
        )
    }

    suspend fun setTheme(value: ThemePref) = set(Keys.THEME, value.key)

    suspend fun setUseSystemColors(value: Boolean) = set(Keys.SYSTEM_COLORS, value)

    suspend fun setTtsRate(value: Float) = set(Keys.TTS_RATE, TtsRate.clamp(value))

    suspend fun setBackgroundPlayback(value: Boolean) = set(Keys.BACKGROUND_PLAYBACK, value)

    suspend fun setExpertMode(value: Boolean) = set(Keys.EXPERT_MODE, value)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
