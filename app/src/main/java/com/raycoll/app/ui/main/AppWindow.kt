package com.raycoll.app.ui.main

import android.app.UiModeManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.ui.theme.RayCollTheme
import com.raycoll.app.ui.theme.resolveDarkMode

// takie same półprzezroczyste tło paska nawigacji jak w domyślnym enableEdgeToEdge()
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Okno Activity w motywie wybranym w aplikacji: paski systemu, tło okna i tryb nocny procesu.
 * Tryb nocny ustawiony w systemie sprawia, że tło okna z XML (widoczne w animacji systemu,
 * zanim Compose narysuje pierwszą klatkę) ma ten sam kolor co aplikacja.
 */
@Composable
fun ComponentActivity.AppWindow(prefs: UserPreferences, content: @Composable () -> Unit) {
    LaunchedEffect(prefs.theme) {
        getSystemService(UiModeManager::class.java).setApplicationNightMode(prefs.theme.nightMode())
    }
    // motyw wybrany w aplikacji może różnić się od systemowego — ikony pasków muszą iść za aplikacją
    val isDark = prefs.theme.resolveDarkMode(isSystemInDarkTheme())
    DisposableEffect(isDark) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { isDark }
        )
        onDispose {}
    }
    RayCollTheme(theme = prefs.theme, useSystemColors = prefs.useSystemColors) {
        // tło okna za Compose (obrót, klawiatura, przewijanie ponad krawędź) w kolorze motywu aplikacji
        val windowBackground = MaterialTheme.colorScheme.background.toArgb()
        DisposableEffect(windowBackground) {
            window.setBackgroundDrawable(windowBackground.toDrawable())
            onDispose {}
        }
        content()
    }
}

private fun ThemePref.nightMode(): Int = when (this) {
    ThemePref.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemePref.DARK -> UiModeManager.MODE_NIGHT_YES
    ThemePref.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
}
