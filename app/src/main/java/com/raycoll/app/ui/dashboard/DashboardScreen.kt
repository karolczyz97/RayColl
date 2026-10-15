package com.raycoll.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.navigation.sharedDeckCard
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.RayCollMotion
import com.raycoll.app.ui.theme.Spacing

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
    // id talii czekającej na potwierdzenie archiwizacji — okno przeżywa obrót ekranu
    var archiveTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.errors_save_failed)
    val messages = rememberSnackbarMessages(snackbarHostState)
    val imported = stringResource(R.string.import_success)
    val archived = stringResource(R.string.archive_success)
    val undo = stringResource(R.string.btn_undo)
    suspend fun showArchived(deckId: String) {
        if (messages.showAction(archived, undo) == SnackbarResult.ActionPerformed) viewModel.restoreDeck(deckId)
    }
    CollectEvents(viewModel.events) { event ->
        when (event) {
            DashboardEvent.SaveFailed -> messages.showLatest(saveFailed)
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
            IconButton(onClick = onStats) {
                Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.stats_title))
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.archive_title))
            }
            IconButton(onClick = onAppSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.app_settings_title))
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState is DashboardUiState.Ready,
                enter = scaleIn(RayCollMotion.spatial()) + fadeIn(RayCollMotion.effects()),
                exit = scaleOut(RayCollMotion.spatial()) + fadeOut(RayCollMotion.effects())
            ) {
                FloatingActionButton(
                    onClick = onImport,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.import_title))
                }
            }
        }
    ) { padding ->
        updateTransition(uiState, label = "dashboard_state").Crossfade(
            modifier = Modifier.padding(padding),
            contentKey = { it::class },
            animationSpec = RayCollMotion.effects()
        ) { state ->
            when (state) {
                DashboardUiState.Loading -> LoadingState()

                is DashboardUiState.Ready -> if (state.decks.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.dashboard_no_groups),
                        action = {
                            Button(onClick = onImport, shape = MaterialTheme.shapes.small) {
                                Text(stringResource(R.string.import_title))
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Spacing.screen,
                            end = Spacing.screen,
                            top = Spacing.xs,
                            bottom = Spacing.fabClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        items(state.decks, key = { it.id }) { deck ->
                            DeckCard(
                                deck = deck,
                                modes = state.modes,
                                onStudy = { onStudy(deck.id) },
                                onBrowse = { onBrowse(deck.id) },
                                onSettings = { onDeckSettings(deck.id) },
                                onArchive = { archiveTargetId = deck.id },
                                onSelectMode = { modeId -> viewModel.setActiveMode(deck.id, modeId) },
                                onManageModes = onManageModes,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    archiveTargetId?.let { deckId ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_archive_title),
            text = pluralStringResource(
                R.plurals.dialog_archive_confirm,
                ArchiveConstants.RETENTION_DAYS,
                ArchiveConstants.RETENTION_DAYS
            ),
            confirmLabel = stringResource(R.string.btn_archive),
            onConfirm = {
                viewModel.archiveDeck(deckId)
                archiveTargetId = null
            },
            onDismiss = { archiveTargetId = null }
        )
    }
}

@Composable
private fun DeckCard(
    deck: DeckSummary,
    modes: List<StudyMode>,
    onStudy: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onArchive: () -> Unit,
    onSelectMode: (modeId: String) -> Unit,
    onManageModes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browseLabel = stringResource(R.string.a11y_item_action, stringResource(R.string.dashboard_browse), deck.name)
    RayCollCard(
        modifier
            .sharedDeckCard(deck.id)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.Top) {
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
                DeckMenu(deckName = deck.name, onSettings = onSettings, onArchive = onArchive)
            }
            CardStatsBar(deck.stats, Modifier.padding(vertical = Spacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(
                    onClick = onBrowse,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MinTouchTarget)
                        .semantics { contentDescription = browseLabel },
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        Icons.Filled.Style,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Text(
                        stringResource(R.string.dashboard_browse),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).clearAndSetSemantics {},
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StudySplitButton(
                    modes = modes,
                    deckName = deck.name,
                    activeModeId = deck.activeMode.id,
                    hasDue = deck.dueCount > 0,
                    onStudy = onStudy,
                    onSelectMode = onSelectMode,
                    onManageModes = onManageModes,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MinTouchTarget)
                )
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
            Icons.Filled.School,
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

@Composable
private fun DeckMenu(deckName: String, onSettings: () -> Unit, onArchive: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.a11y_deck_menu_named, deckName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.route_deck_settings)) },
                onClick = {
                    expanded = false
                    onSettings()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_archive)) },
                onClick = {
                    expanded = false
                    onArchive()
                }
            )
        }
    }
}
