package com.raycoll.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.raycoll.app.R
import com.raycoll.app.ui.appsettings.AppSettingsScreen
import com.raycoll.app.ui.archive.ArchiveScreen
import com.raycoll.app.ui.browse.BrowseScreen
import com.raycoll.app.ui.dashboard.DashboardScreen
import com.raycoll.app.ui.decksettings.DeckSettingsScreen
import com.raycoll.app.ui.imports.ImportScreen
import com.raycoll.app.ui.modedetail.ModeDetailScreen
import com.raycoll.app.ui.stats.StatsScreen
import com.raycoll.app.ui.study.StudyScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RayCollNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
    ) {
        composable<DashboardRoute> {
            DashboardScreen(
                onStudyClick = { id -> navController.navigate(StudyRoute(id)) },
                onBrowseClick = { id -> navController.navigate(BrowseRoute(id)) },
                onDeckSettingsClick = { id -> navController.navigate(DeckSettingsRoute(id)) },
                onImportClick = { navController.navigate(ImportRoute) },
                onStatsClick = { navController.navigate(StatsRoute) },
                onArchiveClick = { navController.navigate(ArchiveRoute) },
                onAppSettingsClick = { navController.navigate(AppSettingsRoute) },
            )
        }
        composable<StudyRoute> { backStack ->
            val route = backStack.toRoute<StudyRoute>()
            StudyScreen(deckId = route.deckId, onExit = { navController.popBackStack() })
        }
        composable<BrowseRoute> { backStack ->
            val route = backStack.toRoute<BrowseRoute>()
            BrowseScreen(deckId = route.deckId, onBack = { navController.popBackStack() })
        }
        composable<DeckSettingsRoute> { backStack ->
            val route = backStack.toRoute<DeckSettingsRoute>()
            DeckSettingsScreen(
                deckId = route.deckId,
                onBack = { navController.popBackStack() },
                onEditMode = { modeId -> navController.navigate(ModeDetailRoute(modeId)) },
            )
        }
        composable<ModeDetailRoute> { backStack ->
            val route = backStack.toRoute<ModeDetailRoute>()
            ModeDetailScreen(modeId = route.modeId, onBack = { navController.popBackStack() })
        }
        composable<ImportRoute> {
            ImportScreen(onFinished = { navController.popBackStack() })
        }
        composable<StatsRoute> {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable<ArchiveRoute> {
            ArchiveScreen(onBack = { navController.popBackStack() })
        }
        composable<AppSettingsRoute> {
            AppSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
