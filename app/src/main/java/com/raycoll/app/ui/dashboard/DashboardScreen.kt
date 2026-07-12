package com.raycoll.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.selectors.getDueCards
import com.raycoll.app.ui.components.SegmentedProgressBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStudyClick: (String) -> Unit,
    onBrowseClick: (String) -> Unit,
    onDeckSettingsClick: (String) -> Unit,
    onImportClick: () -> Unit,
    onStatsClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle(emptyList())
    val modes by viewModel.modes.collectAsStateWithLifecycle(emptyList())
    var archiveTarget by remember { mutableStateOf<FlashcardGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_dashboard)) },
                actions = {
                    IconButton(onClick = onStatsClick) { Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.stats_title)) }
                    IconButton(onClick = onArchiveClick) { Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.archive_title)) }
                    IconButton(onClick = onAppSettingsClick) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.app_settings_title)) }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onImportClick) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.import_title)) } }
    ) { padding ->
        if (decks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.dashboard_no_groups), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onImportClick) { Text(stringResource(R.string.import_title)) }
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(decks, key = { it.id }) { deck ->
                    DeckCard(
                        deck = deck,
                        modes = modes,
                        onStudyClick = onStudyClick,
                        onBrowseClick = onBrowseClick,
                        onDeckSettingsClick = onDeckSettingsClick,
                        onSetActiveMode = viewModel::setActiveMode,
                        onArchive = { archiveTarget = deck },
                    )
                }
            }
        }
    }

    archiveTarget?.let { deck ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text(stringResource(R.string.dialog_archive_title)) },
            text = { Text(stringResource(R.string.dialog_archive_confirm, 14)) },
            confirmButton = {
                TextButton(onClick = { viewModel.archiveDeck(deck.id); archiveTarget = null }) {
                    Text(stringResource(R.string.btn_archive))
                }
            },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}

@Composable
private fun DeckCard(
    deck: FlashcardGroup,
    modes: List<StudyMode>,
    onStudyClick: (String) -> Unit,
    onBrowseClick: (String) -> Unit,
    onDeckSettingsClick: (String) -> Unit,
    onSetActiveMode: (String, String) -> Unit,
    onArchive: () -> Unit,
) {
    val stats = computeCardStats(deck.cards)
    val now = System.currentTimeMillis()
    val dueCount = getDueCards(deck.cards, deck.studyFilter, deck.cardOrder, now).size
    var menuExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    val activeMode = modes.find { it.id == deck.activeModeId }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.route_deck_settings)) }, onClick = { menuExpanded = false; onDeckSettingsClick(deck.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.btn_archive)) }, onClick = { menuExpanded = false; onArchive() })
                }
            }
            SegmentedProgressBar(stats, Modifier.padding(vertical = 8.dp))
            // wybór aktywnego trybu nauki (zapisuje się w talii, przeżywa restart)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { modeMenuExpanded = true }) {
                    Text(activeMode?.name ?: stringResource(R.string.study_modes_title), style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = mode.id == deck.activeModeId, onClick = null)
                                    Text(mode.name)
                                }
                            },
                            onClick = { modeMenuExpanded = false; onSetActiveMode(deck.id, mode.id) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dashboard_due_count, dueCount), style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { onBrowseClick(deck.id) }) { Text(stringResource(R.string.route_browse)) }
                    TextButton(onClick = { onStudyClick(deck.id) }) { Text(stringResource(R.string.route_study)) }
                }
            }
        }
    }
}
