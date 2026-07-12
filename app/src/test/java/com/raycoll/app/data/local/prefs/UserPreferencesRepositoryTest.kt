package com.raycoll.app.data.local.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Port testu preferencji z "RayColl - Kopie" — defaulty + zapis + clamp ttsRate. */
@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryTest {

    @Test
    fun `preferences defaults, writes and ttsRate clamping`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        // defaulty (sekcja 5 planu)
        val defaults = repo.preferences.first()
        assertEquals("system", defaults.themePref)
        assertTrue(defaults.useSystemColors)
        assertEquals(1.0f, defaults.ttsRate, 0.001f)
        assertFalse(defaults.backgroundPlayback)

        // zapisy
        repo.setThemePref("dark")
        repo.setUseSystemColors(false)
        repo.setUiLanguage("en")
        repo.setBackgroundPlayback(true)
        repo.setTtsRate(1.5f)

        var prefs = repo.preferences.first()
        assertEquals("dark", prefs.themePref)
        assertFalse(prefs.useSystemColors)
        assertEquals("en", prefs.uiLanguage)
        assertTrue(prefs.backgroundPlayback)
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
