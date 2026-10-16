package com.raycoll.app

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.raycoll.app.ui.appsettings.AppSettingsActivity
import com.raycoll.app.ui.archive.ArchiveActivity
import com.raycoll.app.ui.browse.BrowseActivity
import com.raycoll.app.ui.dashboard.DashboardScreen
import com.raycoll.app.ui.dashboard.DashboardUiState
import com.raycoll.app.ui.dashboard.DashboardViewModel
import com.raycoll.app.ui.decksettings.DeckSettingsActivity
import com.raycoll.app.ui.imports.ImportActivity
import com.raycoll.app.ui.main.AppActivity
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.navigation.decodeScreenResult
import com.raycoll.app.ui.navigation.encodeScreenResult
import com.raycoll.app.ui.stats.StatsActivity
import com.raycoll.app.ui.study.StudyActivity
import com.raycoll.app.ui.studymodes.StudyModesActivity
import dagger.hilt.android.AndroidEntryPoint

/** Pulpit — pierwsze okno aplikacji; pozostałe ekrany otwiera jako osobne okna. */
@AndroidEntryPoint
class MainActivity : AppActivity() {
    // pulpit zawsze pokazuje bieżące konto
    override val closesOnAccountChange = false

    private val dashboard: DashboardViewModel by viewModels()

    override fun isContentLoading() = dashboard.uiState.value is DashboardUiState.Loading

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { app.preferences.value == null }
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Screen() {
        // wynik ekranu (import, archiwizacja) dla pulpitu; przeżywa obrót i śmierć procesu
        var screenResult by rememberSaveable { mutableStateOf<String?>(null) }
        val onResult: (ScreenResult?) -> Unit = { result -> result?.let { screenResult = encodeScreenResult(it) } }
        val importLauncher = rememberLauncherForActivityResult(ImportActivity.Open, onResult)
        val deckSettingsLauncher = rememberLauncherForActivityResult(DeckSettingsActivity.Open, onResult)
        DashboardScreen(
            result = decodeScreenResult(screenResult),
            onResultConsumed = { screenResult = null },
            onStudy = { openScreen(StudyActivity.intent(this, it)) },
            onBrowse = { openScreen(BrowseActivity.intent(this, it)) },
            onDeckSettings = { openScreen(deckSettingsLauncher, it) },
            onImport = { openScreen(importLauncher, Unit) },
            onStats = { openScreen(StatsActivity.intent(this)) },
            onArchive = { openScreen(ArchiveActivity.intent(this)) },
            onAppSettings = { openScreen(AppSettingsActivity.intent(this)) },
            onManageModes = { openScreen(StudyModesActivity.intent(this)) },
            viewModel = dashboard
        )
    }
}
