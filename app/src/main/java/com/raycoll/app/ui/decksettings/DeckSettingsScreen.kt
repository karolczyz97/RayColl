package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ArchiveConstants
import kotlinx.coroutines.launch
import java.util.Locale

/** Języki dostępne dla stron talii (dropdown). */
val PAGE_LANGUAGE_OPTIONS = listOf("en-US", "pl-PL", "de-DE", "es-ES", "it-IT", "fr-FR")

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
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Nazwa talii — zapis przy utracie fokusu (NIE per znak)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_rename_label)) },
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                    if (!focus.isFocused && deck != null && name != deck?.name && name.isNotBlank()) {
                        viewModel.updateName(deckId, name)
                    }
                },
            )

            // Strony: nazwa (zapis na blur) + język (zapis od razu) + zamiana miejscami
            Text(stringResource(R.string.settings_pages_config), style = MaterialTheme.typography.titleMedium)
            deck?.let { d ->
                for (pageIndex in 0 until d.activePageCount) {
                    PageRow(
                        pageName = d.pageNames.getOrElse(pageIndex) { "" },
                        pageLang = d.pageLanguages.getOrElse(pageIndex) { "en-US" },
                        canMoveUp = pageIndex > 0,
                        canMoveDown = pageIndex < d.activePageCount - 1,
                        onNameSaved = { viewModel.updatePageName(deckId, pageIndex, it) },
                        onLanguageSelected = { viewModel.setPageLanguage(deckId, pageIndex, it) },
                        onMoveUp = { viewModel.movePage(deckId, pageIndex, pageIndex - 1) },
                        onMoveDown = { viewModel.movePage(deckId, pageIndex, pageIndex + 1) },
                    )
                }
            }

            // Liczba aktywnych stron: stepper 2..5 (zmniejszenie NIE kasuje treści)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
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

            Button(onClick = { showArchiveDialog = true }) { Text(stringResource(R.string.settings_archive_btn)) }
            Button(onClick = { showDeleteDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
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
        val deleteErrorMsg = stringResource(R.string.settings_delete_error)
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
                    onClick = {
                        if (deleteToken == expectedToken) {
                            // await na zapisie; błąd → zostań na ekranie + komunikat
                            viewModel.delete(
                                deckId,
                                onDone = { showDeleteDialog = false; onBack() },
                                onError = {
                                    showDeleteDialog = false
                                    scope.launch { snackbarHost.showSnackbar(deleteErrorMsg) }
                                },
                            )
                        }
                    },
                    enabled = deleteToken == expectedToken
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}

@Composable
private fun PageRow(
    pageName: String,
    pageLang: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onNameSaved: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var nameValue by remember(pageName) { mutableStateOf(pageName) }
    var langMenuExpanded by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = nameValue,
            onValueChange = { nameValue = it },
            modifier = Modifier.weight(1f).onFocusChanged { focus ->
                if (!focus.isFocused && nameValue != pageName) onNameSaved(nameValue)
            },
            singleLine = true,
        )
        // język strony — zapis od razu po wyborze
        TextButton(onClick = { langMenuExpanded = true }) {
            Text(pageLang.substringBefore('-').uppercase())
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = langMenuExpanded, onDismissRequest = { langMenuExpanded = false }) {
            PAGE_LANGUAGE_OPTIONS.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang) },
                    onClick = { langMenuExpanded = false; onLanguageSelected(lang) },
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = null) }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = null) }
    }
}
