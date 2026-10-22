package com.raycoll.app.ui.main

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.ui.theme.RayCollTheme

// takie same półprzezroczyste tło paska nawigacji jak w domyślnym enableEdgeToEdge()
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Kolory okna i Compose wynikają z konfiguracji Androida. Tryb aplikacji ustawia RayCollApplication;
 * po jego zmianie system odtwarza Activity ze standardowym zachowaniem stanu.
 */
@Composable
fun ComponentActivity.AppWindow(prefs: UserPreferences, content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    DisposableEffect(isDark) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { isDark }
        )
        onDispose {}
    }
    RayCollTheme(useSystemColors = prefs.useSystemColors) {
        val background = MaterialTheme.colorScheme.background
        val windowBackground = remember { background.toArgb().toDrawable() }
        SideEffect {
            windowBackground.color = background.toArgb()
            if (window.decorView.background !== windowBackground) window.setBackgroundDrawable(windowBackground)
        }
        content()
    }
}
