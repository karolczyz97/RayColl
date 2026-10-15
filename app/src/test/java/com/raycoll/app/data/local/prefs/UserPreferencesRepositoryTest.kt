package com.raycoll.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.PreferencesRule
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Preferencje urządzenia: wartości domyślne, zapis i zakres szybkości mowy. */
@RunWith(RobolectricTestRunner::class)
class UserPreferencesRepositoryTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `corrupted settings file gives defaults and stays writable`() = runBlocking {
        val file = File(folder.root, "settings.preferences_pb").apply { writeBytes(ByteArray(64) { 0xFF.toByte() }) }
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = settingsCorruptionHandler,
                scope = scope,
                produceFile = { file }
            )
            val repo = UserPreferencesRepository(store)
            assertEquals(UserPreferences(), repo.preferences.first())
            repo.setTheme(ThemePref.DARK)
            assertEquals(ThemePref.DARK, repo.preferences.first().theme)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `read failure with IOException gives defaults, other failures are not hidden`() = runBlocking {
        assertEquals(
            UserPreferences(),
            UserPreferencesRepository(failingStore(IOException("disk"))).preferences.first()
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking { UserPreferencesRepository(failingStore(IllegalStateException("bug"))).preferences.first() }
        }
        Unit
    }

    private fun failingStore(error: Throwable) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
    }

    @Test
    fun `preferences defaults, writes and ttsRate clamping`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = testPreferences.repository

        // wartości domyślne
        val defaults = repo.preferences.first()
        assertEquals(ThemePref.SYSTEM, defaults.theme)
        assertTrue(defaults.useSystemColors)
        assertEquals(1.0f, defaults.ttsRate, 0.001f)
        assertTrue(defaults.backgroundPlayback)
        assertFalse(defaults.expertMode)

        // zapisy
        repo.setTheme(ThemePref.DARK)
        repo.setUseSystemColors(false)
        repo.setBackgroundPlayback(false)
        repo.setTtsRate(1.5f)
        repo.setExpertMode(true)

        var prefs = repo.preferences.first()
        assertEquals(ThemePref.DARK, prefs.theme)
        assertFalse(prefs.useSystemColors)
        assertFalse(prefs.backgroundPlayback)
        assertTrue(prefs.expertMode)
        assertEquals(1.5f, prefs.ttsRate, 0.001f)

        // clamp 0.5..2.0
        repo.setTtsRate(2.5f)
        prefs = repo.preferences.first()
        assertEquals(2.0f, prefs.ttsRate, 0.001f)
        repo.setTtsRate(0.2f)
        prefs = repo.preferences.first()
        assertEquals(0.5f, prefs.ttsRate, 0.001f)
    }
}
