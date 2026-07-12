package com.raycoll.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.raycoll.app.ui.navigation.RayCollNavHost
import com.raycoll.app.ui.theme.RayCollTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity: wymagane przez per-app locale (AppCompatDelegate.setApplicationLocales < API 33)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RayCollTheme {
                RayCollNavHost()
            }
        }
    }
}
