package com.raycoll.app.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

// ekran schodzący w głąb (nowy ekran nad nim / powrót na niego) kurczy się słabiej niż ekran, który się zamyka
private const val BEHIND_SCALE = 0.92f
private const val LEAVING_SCALE = 0.9f

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, spatialOffset) + fadeIn(effects)

private fun shrinkAway(): ExitTransition = scaleOut(spatialFloat, targetScale = BEHIND_SCALE) + fadeOut(effects)

private fun growBack(): EnterTransition = scaleIn(spatialFloat, initialScale = BEHIND_SCALE) + fadeIn(effects)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideAway(): ExitTransition =
    scaleOut(spatialFloat, targetScale = LEAVING_SCALE) +
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, spatialOffset) +
        fadeOut(effects)

/** Ekrany z przejściem talii (karta przelatuje) zamykają się bez przesuwania w bok. */
private fun shrinkInPlace(): ExitTransition = scaleOut(spatialFloat, targetScale = LEAVING_SCALE) + fadeOut(effects)

@Composable
fun RayCollNavHost() {
    val navController = rememberNavController()
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { slideIn() },
                exitTransition = { shrinkAway() },
                popEnterTransition = { growBack() },
                popExitTransition = { slideAway() }
            ) {
                screen<DashboardRoute> { entry ->
                    val result by entry.savedStateHandle.getStateFlow<String?>(SCREEN_RESULT, null)
                        .collectAsStateWithLifecycle()
                    DashboardScreen(
                        result = decodeScreenResult(result),
                        onResultConsumed = { entry.savedStateHandle[SCREEN_RESULT] = null },
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
                screen<StudyRoute>(popExit = { shrinkInPlace() }) {
                    StudyScreen(onExit = navController::popIfResumed)
                }
                screen<BrowseRoute>(popExit = { shrinkInPlace() }) {
                    BrowseScreen(onBack = navController::popIfResumed)
                }
                screen<DeckSettingsRoute>(popExit = { shrinkInPlace() }) { entry ->
                    DeckSettingsScreen(
                        onBack = navController::popIfResumed,
                        onClosed = rememberScreenCompletion(navController, entry),
                        onEditMode = { navController.navigate(ModeDetailRoute(it)) }
                    )
                }
                screen<ModeDetailRoute> { entry ->
                    val complete = rememberScreenCompletion(navController, entry)
                    ModeDetailScreen(onBack = navController::popIfResumed, onClosed = { complete(null) })
                }
                screen<ImportRoute> { entry ->
                    val complete = rememberScreenCompletion(navController, entry)
                    ImportScreen(onFinished = navController::popIfResumed, onCompleted = { complete(it) })
                }
                screen<StatsRoute> { StatsScreen(onBack = navController::popIfResumed) }
                screen<ArchiveRoute> { ArchiveScreen(onBack = navController::popIfResumed) }
                screen<AppSettingsRoute> {
                    AppSettingsScreen(onBack = navController::popIfResumed, onStudyModes = {
                        navController.navigate(StudyModesRoute)
                    })
                }
                screen<StudyModesRoute> {
                    StudyModesScreen(
                        onBack = navController::popIfResumed,
                        onEditMode = { navController.navigate(ModeDetailRoute(it)) },
                        onNewMode = { navController.navigate(ModeDetailRoute()) }
                    )
                }
            }
        }
    }
}

/** Ekran z dostępem do przejść talii między ekranami (zakres wspólnego przejścia dostarcza [RayCollNavHost]). */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline popExit: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    crossinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable<T>(popExitTransition = popExit) { entry ->
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            content(entry)
        }
    }
}

/** Powrót z ekranu; ignoruje drugie szybkie dotknięcie, gdy ekran już się zamyka. */
private fun NavHostController.popIfResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        popBackStack()
    }
}
