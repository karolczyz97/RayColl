package com.raycoll.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.CardStatsBar
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.Pill
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SwipeAction
import com.raycoll.app.ui.components.SwipeReveal
import com.raycoll.app.ui.components.animateListItem
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.rememberSwipeRevealState
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.navigation.sharedDeckCard
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Minimalny rozmiar celu dotyku (Material). */
internal val MinTouchTarget = 48.dp

@OptIn(ExperimentalAnimationApi::class)
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
                    LazyColumn(
                        contentPadding = Spacing.screenContent(bottom = Spacing.fabClearance),
                        verticalArrangement = Arrangement.spacedBy(Spacing.item)
                    ) {
                        val hintDeckId = state.decks.firstOrNull()?.id?.takeIf { deckHintsLeft > 0 && !hintPlayed }
                        items(state.decks, key = { it.id }) { deck ->
                            DeckCard(
                                deck = deck,
                                modes = state.modes,
                                onStudy = { onStudy(deck.id) },
                                onBrowse = { onBrowse(deck.id) },
                                onSettings = { onDeckSettings(deck.id) },
                                // bez okna potwierdzenia: talia trafia do Archiwum, a komunikat pozwala cofnąć
                                onArchive = { viewModel.archiveDeck(deck.id) },
                                failedWriteKey = failedWriteKey,
                                onSelectMode = { modeId -> viewModel.setActiveMode(deck.id, modeId) },
                                onManageModes = onManageModes,
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
                                },
                                modifier = Modifier.animateListItem(this)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCard(
    failedWriteKey: Int,
    deck: DeckSummary,
    modes: List<StudyMode>,
    onStudy: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onArchive: () -> Unit,
    onSelectMode: (modeId: String) -> Unit,
    onManageModes: () -> Unit,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = false,
    onHintShown: () -> Unit = {}
) {
    val browseLabel = stringResource(R.string.a11y_item_action, stringResource(R.string.dashboard_browse), deck.name)
    val colors = MaterialTheme.colorScheme
    val reveal = rememberSwipeRevealState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // inna talia odsłoniła akcje — ta chowa swoje
    LaunchedEffect(revealed) {
        if (!revealed && reveal.isOpen) reveal.close()
    }
    SwipeReveal(
        resetKey = failedWriteKey,
        actions = listOf(
            SwipeAction(
                label = stringResource(R.string.dashboard_deck_options),
                icon = painterResource(R.drawable.ic_settings),
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                onClick = onSettings
            ),
            SwipeAction(
                label = stringResource(R.string.btn_archive),
                icon = painterResource(R.drawable.ic_archive),
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                removesItem = true,
                onClick = onArchive
            )
        ),
        state = reveal,
        onOpenChange = onRevealedChange,
        showHint = showHint,
        onHintShown = onHintShown,
        modifier = modifier
    ) {
        RayCollCard(
            Modifier
                .sharedDeckCard(deck.id)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    // dotknięcie: podgląd talii (a gdy akcje są odsłonięte — schowanie ich); przytrzymanie odsłania akcje
                    .combinedClickable(
                        onClickLabel = browseLabel,
                        onLongClickLabel = stringResource(R.string.dashboard_deck_options),
                        onClick = { if (reveal.isOpen) scope.launch { reveal.close() } else onBrowse() },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { reveal.open() }
                        }
                    )
                    .padding(Spacing.l)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(end = Spacing.s)
                    ) {
                        Text(
                            deck.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.sharedDeckTitle(deck.id)
                        )
                        DeckSubtitle(deck)
                    }
                    StudySplitButton(
                        modes = modes,
                        deckName = deck.name,
                        activeModeId = deck.activeMode.id,
                        hasDue = deck.dueCount > 0,
                        onStudy = onStudy,
                        onSelectMode = onSelectMode,
                        onManageModes = onManageModes
                    )
                }
                CardStatsBar(deck.stats, Modifier.padding(top = Spacing.m))
            }
        }
    }
}

@Composable
private fun DeckSubtitle(deck: DeckSummary) {
    Row(
        modifier = Modifier.padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Icon(
            painterResource(R.drawable.ic_tune),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            deck.activeMode.displayName(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        val hasDue = deck.dueCount > 0
        Pill(
            text = stringResource(R.string.dashboard_due_count, deck.dueCount),
            container = if (hasDue) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            content = if (hasDue) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
