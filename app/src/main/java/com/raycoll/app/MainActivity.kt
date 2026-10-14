package com.raycoll.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.ui.components.MigrationDialog
import com.raycoll.app.ui.main.MainViewModel
import com.raycoll.app.ui.navigation.RayCollNavHost
import com.raycoll.app.ui.theme.RayCollTheme
import dagger.hilt.android.AndroidEntryPoint

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
            RayCollTheme(theme = prefs.theme, useSystemColors = prefs.useSystemColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RayCollNavHost()
                    if (migrationRequired) MigrationDialog(onChoose = viewModel::resolveMigration)
                }
            }
        }
    }
}
