package com.raycoll.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.data.local.prefs.UserPreferences
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.ui.navigation.RayCollNavHost
import com.raycoll.app.ui.theme.RayCollTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// AppCompatActivity: wymagane przez per-app locale (AppCompatDelegate.setApplicationLocales < API 33)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefsRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // motyw i dynamic colors z DataStore — bez tego przełączniki w
            // ustawieniach nie miały żadnego efektu (theme brał defaulty)
            val prefs by prefsRepository.preferences.collectAsStateWithLifecycle(UserPreferences())
            RayCollTheme(themePref = prefs.themePref, useSystemColors = prefs.useSystemColors) {
                RayCollNavHost()
            }
        }
    }
}
