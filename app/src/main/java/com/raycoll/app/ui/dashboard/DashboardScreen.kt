package com.raycoll.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.core.moved
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.moveActions
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.navigation.ScreenResult

@Composable
fun DashboardScreen(
    onStudy: (deckId: String) -> Unit,
    onBrowse: (deckId: String) -> Unit,
    onDeckSettings: (deckId: String) -> Unit,
    onImport: () -> Unit,
    onStats: () -> Unit,
    onArchive: () -> Unit,
    onAppSettings: () -> Unit,
    onManageModes: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    result: ScreenResult? = null,
    onResultConsumed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // talia z odsłoniętymi akcjami — odsłonięcie innej chowa poprzednią
    var revealedDeckId by remember { mutableStateOf<String?>(null) }
    // podpowiedź gestu raz na wejście na pulpit, dopóki użytkownik sam go nie użyje (maks. kilka razy)
    var hintPlayed by rememberSaveable { mutableStateOf(false) }
    val deckHintsLeft by viewModel.deckSwipeHintsLeft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.errors_save_failed)
    var failedWriteKey by remember { mutableIntStateOf(0) }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val imported = stringResource(R.string.import_success)
    val archived = stringResource(R.string.archive_success)
    val undo = stringResource(R.string.btn_undo)
    val moveDeckUp = stringResource(R.string.dashboard_move_deck_up)
    val moveDeckDown = stringResource(R.string.dashboard_move_deck_down)
    suspend fun showArchived(deckId: String) {
        if (messages.showAction(archived, undo) == SnackbarResult.ActionPerformed) viewModel.restoreDeck(deckId)
    }
    CollectEvents(viewModel.events) { event ->
        when (event) {
            DashboardEvent.SaveFailed -> {
                failedWriteKey++
                messages.showLatest(saveFailed)
            }

            is DashboardEvent.Archived -> showArchived(event.deckId)
        }
    }
    LaunchedEffect(result) {
        if (result != null) {
            when (result) {
                ScreenResult.Imported -> messages.showLatest(imported)
                is ScreenResult.Archived -> showArchived(result.deckId)
            }
            onResultConsumed()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_dashboard),
        // AndroidView zgłasza fling przed jego wykonaniem; pasek śledzi delty RecyclerView,
        // zamiast w tym samym czasie zmieniać rozmiar listy własną animacją.
        animateTitleOnRelease = false,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onStats) {
                Icon(
                    painterResource(R.drawable.ic_bar_chart),
                    contentDescription = stringResource(R.string.stats_title)
                )
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onArchive) {
                Icon(
                    painterResource(R.drawable.ic_archive),
                    contentDescription = stringResource(R.string.archive_title)
                )
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onAppSettings) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.app_settings_title)
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState is DashboardUiState.Ready,
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                FloatingActionButton(
                    onClick = onImport,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.import_title))
                }
            }
        }
    ) { padding ->
        updateTransition(uiState, label = "dashboard_state").Crossfade(
            modifier = Modifier.padding(padding),
            contentKey = { it::class },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
        ) { state ->
            when (state) {
                DashboardUiState.Loading -> LoadingState()

                is DashboardUiState.Ready -> if (state.decks.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.dashboard_no_groups),
                        action = {
                            Button(onClick = onImport, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.import_title))
                            }
                        }
                    )
                } else {
                    DeckList(
                        decks = state.decks,
                        resetKey = failedWriteKey,
                        onReorder = viewModel::reorderDecks,
                        onDragStarted = {
                            revealedDeckId = null
                            hintPlayed = true
                        }
                    ) { deck, startDrag ->
                        val position = state.decks.indexOfFirst { it.id == deck.id }
                        val hintDeckId = state.decks.firstOrNull()?.id?.takeIf { deckHintsLeft > 0 && !hintPlayed }
                        DashboardDeckItem(
                            deck = deck,
                            modes = state.modes,
                            onStudy = { onStudy(deck.id) },
                            onSettings = { onDeckSettings(deck.id) },
                            onArchive = { viewModel.archiveDeck(deck.id) },
                            onSelectMode = { modeId -> viewModel.setActiveMode(deck.id, modeId) },
                            onManageModes = onManageModes,
                            onBrowse = { onBrowse(deck.id) },
                            onStartDrag = startDrag,
                            reorderActions = moveActions(
                                position,
                                state.decks.size,
                                moveDeckUp,
                                moveDeckDown
                            ) { from, to -> viewModel.reorderDecks(state.decks.map { it.id }.moved(from, to)) },
                            failedWriteKey = failedWriteKey,
                            revealed = revealedDeckId == deck.id,
                            showHint = deck.id == hintDeckId,
                            onHintShown = {
                                hintPlayed = true
                                viewModel.deckSwipeHintShown()
                            },
                            onRevealedChange = { open ->
                                if (open) {
                                    viewModel.deckSwipeLearned()
                                    revealedDeckId = deck.id
                                } else if (revealedDeckId == deck.id) {
                                    revealedDeckId = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
