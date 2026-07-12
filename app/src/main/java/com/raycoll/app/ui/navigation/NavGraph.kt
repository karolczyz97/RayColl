package com.raycoll.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.raycoll.app.R
import com.raycoll.app.ui.dashboard.DashboardScreen
import com.raycoll.app.ui.study.StudyScreen
import com.raycoll.app.ui.browse.BrowseScreen
import com.raycoll.app.ui.stats.StatsScreen
import com.raycoll.app.ui.archive.ArchiveScreen
import com.raycoll.app.ui.imports.ImportScreen
import com.raycoll.app.ui.decksettings.DeckSettingsScreen
import com.raycoll.app.ui.modedetail.StudyModeDetailScreen
import com.raycoll.app.ui.appsettings.AppSettingsScreen
import com.raycoll.app.ui.theme.RayCollTheme

enum class TopLevelRoute(val titleRes: Int, val icon: ImageVector) {
    Dashboard(R.string.nav_dashboard, Icons.Default.Home),
    Stats(R.string.stats_title, Icons.Default.BarChart),
    Archive(R.string.archive_title, Icons.Default.Archive),
}

@Composable
fun RayCollNavHost(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = TopLevelRoute.entries.any { it.name == currentRoute }

    val navigate: (String) -> Unit = { route ->
        navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    }

    RayCollTheme {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        TopLevelRoute.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.name,
                                onClick = { navigate(dest.name) },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.titleRes)) },
                                label = { Text(stringResource(dest.titleRes)) },
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(navController = navController, startDestination = TopLevelRoute.Dashboard.name, modifier = Modifier.padding(padding)) {
                composable(TopLevelRoute.Dashboard.name) {
                    DashboardScreen(
                        onStudy = { navController.navigate("study/$it") },
                        onBrowse = { navController.navigate("browse/$it") },
                        onSettings = { navController.navigate("decksettings/$it") },
                        onImport = { navController.navigate("import") },
                        onAppSettings = { navController.navigate("appsettings") },
                    )
                }
                composable(TopLevelRoute.Stats.name) { StatsScreen() }
                composable(TopLevelRoute.Archive.name) { ArchiveScreen() }
                composable("study/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { StudyScreen(it.arguments?.getString("groupId") ?: "") }
                composable("browse/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { BrowseScreen(it.arguments?.getString("groupId") ?: "") }
                composable("decksettings/{groupId}", arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { DeckSettingsScreen(it.arguments?.getString("groupId") ?: "") }
                composable("import") { ImportScreen() }
                composable("studymode/{modeId}", arguments = listOf(navArgument("modeId") { type = NavType.StringType })) { StudyModeDetailScreen(modeId = it.arguments?.getString("modeId") ?: "", onBack = { navController.popBackStack() }) }
                composable("appsettings") { AppSettingsScreen(onStudyModes = { navController.navigate("studymode/classic") }) }
            }
        }
    }
}
