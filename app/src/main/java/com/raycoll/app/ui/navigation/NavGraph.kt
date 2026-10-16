package com.raycoll.app.ui.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import androidx.savedstate.serialization.SavedStateConfiguration
import com.raycoll.app.ui.appsettings.AppSettingsScreen
import com.raycoll.app.ui.archive.ArchiveScreen
import com.raycoll.app.ui.browse.BrowseScreen
import com.raycoll.app.ui.browse.BrowseViewModel
import com.raycoll.app.ui.dashboard.DashboardScreen
import com.raycoll.app.ui.decksettings.DeckSettingsScreen
import com.raycoll.app.ui.decksettings.DeckSettingsViewModel
import com.raycoll.app.ui.imports.ImportScreen
import com.raycoll.app.ui.modedetail.ModeDetailScreen
import com.raycoll.app.ui.modedetail.ModeDetailViewModel
import com.raycoll.app.ui.stats.StatsScreen
import com.raycoll.app.ui.study.StudyScreen
import com.raycoll.app.ui.study.StudyViewModel
import com.raycoll.app.ui.studymodes.StudyModesScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// stos ekranów zapisywany przy obrocie i śmierci procesu; każdy ekran musi być tu zarejestrowany
private val navKeysConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(DashboardRoute::class)
            subclass(StudyRoute::class)
            subclass(BrowseRoute::class)
            subclass(DeckSettingsRoute::class)
            subclass(ModeDetailRoute::class)
            subclass(ImportRoute::class)
            subclass(StatsRoute::class)
            subclass(ArchiveRoute::class)
            subclass(AppSettingsRoute::class)
            subclass(StudyModesRoute::class)
        }
    }
}

/** Przejścia między ekranami na stosie [backStack]. */
private class Navigator(
    private val backStack: NavBackStack<NavKey>,
    private val predictiveBack: MutableState<Boolean>
) {
    fun open(key: NavKey) {
        predictiveBack.value = false
        backStack.add(key)
    }

    /** Zamyka [key], o ile nadal jest na wierzchu (drugie szybkie dotknięcie „wstecz” nic nie robi). */
    fun close(key: NavKey) {
        if (backStack.size > 1 && backStack.last() == key) {
            predictiveBack.value = false
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun isCurrent(key: NavKey) = backStack.lastOrNull() == key
}

@Composable
fun RayCollNavHost() {
    val backStack = rememberNavBackStack(navKeysConfiguration, DashboardRoute)
    // gest „wstecz” trwa (także dokończenie po puszczeniu palca) — do następnego zwykłego przejścia
    val predictiveBack = remember { mutableStateOf(false) }
    val navigator = remember(backStack) { Navigator(backStack, predictiveBack) }
    // wynik ekranu (import, archiwizacja) dla pulpitu; przeżywa obrót i śmierć procesu
    var screenResult by rememberSaveable { mutableStateOf<String?>(null) }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<DashboardRoute> {
                DashboardScreen(
                    result = decodeScreenResult(screenResult),
                    onResultConsumed = { screenResult = null },
                    onStudy = { navigator.open(StudyRoute(it)) },
                    onBrowse = { navigator.open(BrowseRoute(it)) },
                    onDeckSettings = { navigator.open(DeckSettingsRoute(it)) },
                    onImport = { navigator.open(ImportRoute) },
                    onStats = { navigator.open(StatsRoute) },
                    onArchive = { navigator.open(ArchiveRoute) },
                    onAppSettings = { navigator.open(AppSettingsRoute) },
                    onManageModes = { navigator.open(StudyModesRoute) }
                )
            }
            entry<StudyRoute> { key ->
                StudyScreen(
                    onExit = navigator.rememberClose(key),
                    viewModel = hiltViewModel<StudyViewModel, StudyViewModel.Factory> { it.create(key.deckId) }
                )
            }
            entry<BrowseRoute> { key ->
                BrowseScreen(
                    onBack = navigator.rememberClose(key),
                    viewModel = hiltViewModel<BrowseViewModel, BrowseViewModel.Factory> { it.create(key.deckId) }
                )
            }
            entry<DeckSettingsRoute> { key ->
                DeckSettingsScreen(
                    onBack = navigator.rememberClose(key),
                    onClosed = navigator.rememberCompletion(key) { screenResult = it },
                    onEditMode = { navigator.open(ModeDetailRoute(it)) },
                    viewModel = hiltViewModel<DeckSettingsViewModel, DeckSettingsViewModel.Factory> {
                        it.create(key.deckId)
                    }
                )
            }
            entry<ModeDetailRoute> { key ->
                val complete = navigator.rememberCompletion(key) {}
                ModeDetailScreen(
                    onBack = navigator.rememberClose(key),
                    onClosed = { complete(null) },
                    viewModel = hiltViewModel<ModeDetailViewModel, ModeDetailViewModel.Factory> {
                        it.create(key.modeId)
                    }
                )
            }
            entry<ImportRoute> { key ->
                ImportScreen(
                    onFinished = navigator.rememberClose(key),
                    onCompleted = navigator.rememberCompletion(key) { screenResult = it }
                )
            }
            entry<StatsRoute> { key -> StatsScreen(onBack = navigator.rememberClose(key)) }
            entry<ArchiveRoute> { key -> ArchiveScreen(onBack = navigator.rememberClose(key)) }
            entry<AppSettingsRoute> { key ->
                AppSettingsScreen(
                    onBack = navigator.rememberClose(key),
                    onStudyModes = { navigator.open(StudyModesRoute) }
                )
            }
            entry<StudyModesRoute> { key ->
                StudyModesScreen(
                    onBack = navigator.rememberClose(key),
                    onEditMode = { navigator.open(ModeDetailRoute(it)) },
                    onNewMode = { navigator.open(ModeDetailRoute()) }
                )
            }
        }
    )

    SharedTransitionLayout {
        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategies = listOf(SinglePaneSceneStrategy()),
            onBack = { navigator.close(backStack.last()) }
        )
        val navigationEventState = rememberNavigationEventState(sceneState)
        NavigationBackHandler(sceneState, navigationEventState, onBackCompleted = {
            backStack.removeAt(backStack.lastIndex)
        })
        val gestureInProgress = navigationEventState.transitionState is NavigationEventTransitionState.InProgress
        LaunchedEffect(gestureInProgress) {
            if (gestureInProgress) predictiveBack.value = true
        }
        val sharedConfig = remember(navigationEventState) {
            DeckSharedContentConfig {
                predictiveBack.value ||
                    navigationEventState.transitionState is NavigationEventTransitionState.InProgress
            }
        }

        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalDeckSharedContentConfig provides sharedConfig
        ) {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = navigationEventState,
                // domyślne przejścia Navigation 3 (także przewijane gestem „wstecz”)
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Powrót z ekranu [key]; ignorowany, gdy ekran jest w trakcie przejścia albo już się zamyka. */
@Composable
private fun Navigator.rememberClose(key: NavKey): () -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(this, key, lifecycle) {
        { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) close(key) }
    }
}

/** Zamknięcie ekranu [key] z wynikiem przekazanym do [onResult] (zakodowany [ScreenResult]). */
@Composable
private fun Navigator.rememberCompletion(key: NavKey, onResult: (String?) -> Unit): (ScreenResult?) -> Unit =
    rememberScreenCompletion(isCurrent = { isCurrent(key) }) { result ->
        result?.let(onResult)
        close(key)
    }
