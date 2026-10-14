package com.raycoll.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.ui.components.MigrationDialog
import com.raycoll.app.ui.main.MainViewModel
import com.raycoll.app.ui.navigation.RayCollNavHost
import com.raycoll.app.ui.theme.RayCollTheme
import dagger.hilt.android.AndroidEntryPoint

// takie same półprzezroczyste tło paska nawigacji jak w domyślnym enableEdgeToEdge()
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

// AppCompatActivity: język aplikacji wybrany w ustawieniach (AppCompatDelegate) działa też przed Androidem 13
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.preferences.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val migrationRequired by viewModel.migrationRequired.collectAsStateWithLifecycle()
            val prefs = preferences ?: return@setContent
            // motyw wybrany w aplikacji może różnić się od systemowego — ikony pasków muszą iść za aplikacją
            val isDark = when (prefs.theme) {
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
                ThemePref.SYSTEM -> isSystemInDarkTheme()
            }
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RayCollNavHost()
                    if (migrationRequired) MigrationDialog(onChoose = viewModel::resolveMigration)
                }
            }
        }
    }
}
