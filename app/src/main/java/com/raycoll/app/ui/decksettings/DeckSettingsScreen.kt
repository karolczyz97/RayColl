package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ArchiveConstants
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckSettingsScreen(
    deckId: String,
    onBack: () -> Unit,
    onEditMode: (String?) -> Unit,
    viewModel: DeckSettingsViewModel = hiltViewModel(),
) {
    val deck by viewModel.deck.collectAsStateWithLifecycle(null)
    val modes by viewModel.modes.collectAsStateWithLifecycle()
    var modeToDelete by remember { mutableStateOf<String?>(null) }
    var modeToReset by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var activePageCount by remember { mutableStateOf(2) }
    var studyFilter by remember { mutableStateOf("new+review") }
    var cardOrder by remember { mutableStateOf("sequential") }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteToken by remember { mutableStateOf("") }

    LaunchedEffect(deckId) { viewModel.loadDeck(deckId) }
    LaunchedEffect(deck) {
        deck?.let {
            name = it.name
            activePageCount = it.activePageCount
            studyFilter = it.studyFilter
            cardOrder = it.cardOrder
        }
    }

    val locale = Locale.getDefault().language
    val expectedToken = ArchiveConstants.DELETE_TOKENS[locale] ?: "DELETE"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_deck_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_rename_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Active page count stepper
            Text(stringResource(R.string.settings_pages_config), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { if (activePageCount > 2) { activePageCount--; viewModel.setVisiblePageCount(deckId, activePageCount) } }) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                }
                Text("$activePageCount", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { if (activePageCount < 5) { activePageCount++; viewModel.setVisiblePageCount(deckId, activePageCount) } }) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                }
            }

            // Study filter
            Text(stringResource(R.string.settings_study_scope), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("new+review" to R.string.filter_new_review, "new" to R.string.filter_new, "review" to R.string.filter_review, "all" to R.string.filter_all).forEach { (value, label) ->
                    TextButton(onClick = { studyFilter = value; viewModel.setStudyFilter(deckId, value) }) {
                        Text(stringResource(label), color = if (studyFilter == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Card order
            Text(stringResource(R.string.settings_card_order), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sequential" to R.string.settings_card_order_sequential, "random" to R.string.settings_card_order_random, "hardest" to R.string.settings_card_order_hardest).forEach { (value, label) ->
                    TextButton(onClick = { cardOrder = value; viewModel.setCardOrder(deckId, value) }) {
                        Text(stringResource(label), color = if (cardOrder == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Tryby nauki: wybór aktywnego + edycja/usuwanie/reset + nowy
            Text(stringResource(R.string.study_modes_title), style = MaterialTheme.typography.titleMedium)
            modes.forEach { mode ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = deck?.activeModeId == mode.id,
                        onClick = { viewModel.setActiveMode(deckId, mode.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(mode.name)
                        if (mode.isBuiltIn) {
                            Text(stringResource(R.string.mode_editor_builtin_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    IconButton(onClick = { onEditMode(mode.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.mode_list_edit))
                    }
                    if (mode.isBuiltIn) {
                        IconButton(onClick = { modeToReset = mode.id }) {
                            Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.mode_list_reset))
                        }
                    } else {
                        IconButton(onClick = { modeToDelete = mode.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            TextButton(onClick = { onEditMode(null) }) { Text(stringResource(R.string.mode_list_new)) }

            Button(onClick = { viewModel.updateName(deckId, name) }) { Text(stringResource(R.string.btn_save)) }
            Button(onClick = { showArchiveDialog = true }) { Text(stringResource(R.string.settings_archive_btn)) }
            Button(onClick = { showDeleteDialog = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.btn_delete))
            }
        }
    }

    modeToDelete?.let { modeId ->
        AlertDialog(
            onDismissRequest = { modeToDelete = null },
            title = { Text(stringResource(R.string.mode_list_delete_title)) },
            text = { Text(stringResource(R.string.mode_list_delete_desc)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteMode(modeId); modeToDelete = null }) { Text(stringResource(R.string.btn_delete)) } },
            dismissButton = { TextButton(onClick = { modeToDelete = null }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }

    modeToReset?.let { modeId ->
        AlertDialog(
            onDismissRequest = { modeToReset = null },
            title = { Text(stringResource(R.string.mode_list_reset_title)) },
            text = { Text(stringResource(R.string.mode_list_reset_desc)) },
            confirmButton = { TextButton(onClick = { viewModel.resetMode(modeId); modeToReset = null }) { Text(stringResource(R.string.mode_list_reset)) } },
            dismissButton = { TextButton(onClick = { modeToReset = null }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(R.string.dialog_archive_title)) },
            text = { Text(stringResource(R.string.dialog_archive_confirm, 14)) },
            confirmButton = { TextButton(onClick = { showArchiveDialog = false; viewModel.archive(deckId); onBack() }) { Text(stringResource(R.string.btn_archive)) } },
            dismissButton = { TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_permanent_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_delete_permanent_desc, name))
                    OutlinedTextField(value = deleteToken, onValueChange = { deleteToken = it }, label = { Text(expectedToken) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (deleteToken == expectedToken) { viewModel.delete(deckId); showDeleteDialog = false; onBack() } },
                    enabled = deleteToken == expectedToken
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}
