package com.raycoll.app.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raycoll.app.ui.appsettings.AppSettingsScreen
import com.raycoll.app.ui.archive.ArchiveScreen
import com.raycoll.app.ui.browse.BrowseScreen
import com.raycoll.app.ui.dashboard.DashboardScreen
import com.raycoll.app.ui.decksettings.DeckSettingsScreen
import com.raycoll.app.ui.imports.ImportScreen
import com.raycoll.app.ui.modedetail.ModeDetailScreen
import com.raycoll.app.ui.stats.StatsScreen
import com.raycoll.app.ui.study.StudyScreen
import com.raycoll.app.ui.studymodes.StudyModesScreen
import com.raycoll.app.ui.theme.RayCollMotion

private val spatialOffset = RayCollMotion.spatial<IntOffset>()
private val spatialFloat = RayCollMotion.spatial<Float>()
private val effects = RayCollMotion.effects<Float>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, spatialOffset) + fadeIn(effects)

private fun shrinkAway(): ExitTransition = scaleOut(spatialFloat, targetScale = 0.92f) + fadeOut(effects)

private fun growBack(): EnterTransition = scaleIn(spatialFloat, initialScale = 0.92f) + fadeIn(effects)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideAway(): ExitTransition =
    scaleOut(spatialFloat, targetScale = 0.9f) +
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, spatialOffset) +
        fadeOut(effects)

/** Ekrany z przejściem talii (karta przelatuje) zamykają się bez przesuwania w bok. */
private fun shrinkInPlace(): ExitTransition = scaleOut(spatialFloat, targetScale = 0.9f) + fadeOut(effects)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RayCollNavHost() {
    val navController = rememberNavController()
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = { slideIn() },
            exitTransition = { shrinkAway() },
            popEnterTransition = { growBack() },
            popExitTransition = { slideAway() }
        ) {
            screen<DashboardRoute>(this@SharedTransitionLayout) {
                DashboardScreen(
                    onStudy = { navController.navigate(StudyRoute(it)) },
                    onBrowse = { navController.navigate(BrowseRoute(it)) },
                    onDeckSettings = { navController.navigate(DeckSettingsRoute(it)) },
                    onImport = { navController.navigate(ImportRoute) },
                    onStats = { navController.navigate(StatsRoute) },
                    onArchive = { navController.navigate(ArchiveRoute) },
                    onAppSettings = { navController.navigate(AppSettingsRoute) },
                    onManageModes = { navController.navigate(StudyModesRoute) }
                )
            }
            screen<StudyRoute>(this@SharedTransitionLayout, popExit = { shrinkInPlace() }) {
                StudyScreen(onExit = navController::up)
            }
            screen<BrowseRoute>(this@SharedTransitionLayout, popExit = { shrinkInPlace() }) {
                BrowseScreen(onBack = navController::up)
            }
            screen<DeckSettingsRoute>(this@SharedTransitionLayout, popExit = { shrinkInPlace() }) {
                DeckSettingsScreen(onBack = navController::up, onEditMode = {
                    navController.navigate(ModeDetailRoute(it))
                })
            }
            screen<ModeDetailRoute>(this@SharedTransitionLayout) { ModeDetailScreen(onBack = navController::up) }
            screen<ImportRoute>(this@SharedTransitionLayout) { ImportScreen(onFinished = navController::up) }
            screen<StatsRoute>(this@SharedTransitionLayout) { StatsScreen(onBack = navController::up) }
            screen<ArchiveRoute>(this@SharedTransitionLayout) { ArchiveScreen(onBack = navController::up) }
            screen<AppSettingsRoute>(this@SharedTransitionLayout) {
                AppSettingsScreen(onBack = navController::up, onStudyModes = {
                    navController.navigate(StudyModesRoute)
                })
            }
            screen<StudyModesRoute>(this@SharedTransitionLayout) {
                StudyModesScreen(
                    onBack = navController::up,
                    onEditMode = { navController.navigate(ModeDetailRoute(it)) },
                    onNewMode = { navController.navigate(ModeDetailRoute()) }
                )
            }
        }
    }
}

/** Ekran z dostępem do przejść talii między ekranami. */
@OptIn(ExperimentalSharedTransitionApi::class)
private inline fun <reified T : Any> NavGraphBuilder.screen(
    sharedScope: SharedTransitionScope,
    noinline popExit: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    crossinline content: @Composable AnimatedContentScope.() -> Unit
) {
    composable<T>(popExitTransition = popExit) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedScope,
            LocalNavAnimatedVisibilityScope provides this
        ) {
            content()
        }
    }
}

/** Powrót z ekranu; ignoruje drugie szybkie dotknięcie, gdy ekran już się zamyka. */
private fun NavHostController.up() {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        popBackStack()
    }
}
