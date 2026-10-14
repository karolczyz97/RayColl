package com.raycoll.app.data.local.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Preferencje urządzenia: wartości domyślne, zapis i zakres szybkości mowy. */
@RunWith(RobolectricTestRunner::class)
class UserPreferencesRepositoryTest {

    @Test
    fun `preferences defaults, writes and ttsRate clamping`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        // wartości domyślne
        val defaults = repo.preferences.first()
        assertEquals(ThemePref.SYSTEM, defaults.theme)
        assertTrue(defaults.useSystemColors)
        assertEquals(1.0f, defaults.ttsRate, 0.001f)
        assertTrue(defaults.backgroundPlayback)

        // zapisy
        repo.setTheme(ThemePref.DARK)
        repo.setUseSystemColors(false)
        repo.setBackgroundPlayback(false)
        repo.setTtsRate(1.5f)

        var prefs = repo.preferences.first()
        assertEquals(ThemePref.DARK, prefs.theme)
        assertFalse(prefs.useSystemColors)
        assertFalse(prefs.backgroundPlayback)
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
