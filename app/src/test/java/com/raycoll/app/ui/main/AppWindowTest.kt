package com.raycoll.app.ui.main

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.ui.theme.DarkBackground
import com.raycoll.app.ui.theme.LightBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppWindowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "night")
    fun `a new light preference waits for Android instead of recoloring a dark window early`() {
        assertWindowColors(ThemePref.LIGHT, DarkBackground, lightSystemBars = false)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `a new dark preference waits for Android instead of recoloring a light window early`() {
        assertWindowColors(ThemePref.DARK, LightBackground, lightSystemBars = true)
    }

    private fun assertWindowColors(preference: ThemePref, expected: Color, lightSystemBars: Boolean) {
        var background = Color.Unspecified
        compose.setContent {
            compose.activity.AppWindow(UserPreferences(theme = preference, useSystemColors = false)) {
                val color = MaterialTheme.colorScheme.background
                SideEffect { background = color }
                Text("Content")
            }
        }
        compose.waitForIdle()
        assertEquals(expected, background)
        val window = compose.activity.window
        assertEquals(
            lightSystemBars,
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        )
    }

    @Test
    fun `every app screen lets Android recreate it when night mode changes`() {
        val context = compose.activity
        val activities = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
        ).activities.orEmpty().filter { it.name.startsWith("com.raycoll.app.") }
        assertTrue("No app screens were found in the merged manifest", activities.isNotEmpty())
        activities.forEach {
            assertEquals(it.name, 0, it.configChanges and ActivityInfo.CONFIG_UI_MODE)
        }
    }
}
