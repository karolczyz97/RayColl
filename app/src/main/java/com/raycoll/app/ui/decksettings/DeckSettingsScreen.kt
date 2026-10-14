package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeActions
import com.raycoll.app.ui.components.ModeBadges
import com.raycoll.app.ui.components.PersistedTextField
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.navigation.sharedDeckCard
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.Spacing

@Composable
fun DeckSettingsScreen(
    onBack: () -> Unit,
    onEditMode: (modeId: String?) -> Unit,
    viewModel: DeckSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailed = stringResource(R.string.settings_delete_error)
    val saveFailed = stringResource(R.string.errors_save_failed)

    CollectEvents(viewModel.events) { event ->
        when (event) {
            DeckSettingsEvent.Closed -> onBack()
            DeckSettingsEvent.DeleteFailed -> snackbarHostState.showSnackbar(deleteFailed)
            DeckSettingsEvent.SaveFailed -> snackbarHostState.showSnackbar(saveFailed)
        }
    }

    val ready = uiState as? DeckSettingsUiState.Ready
    ScreenScaffold(
        title = ready?.deck?.name ?: stringResource(R.string.route_deck_settings),
        titleModifier = Modifier.sharedDeckTitle(viewModel.deckId),
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            DeckSettingsUiState.Loading -> LoadingState(contentModifier)
            DeckSettingsUiState.DeckMissing -> ErrorState(stringResource(R.string.errors_deck_missing), contentModifier)
            is DeckSettingsUiState.Ready -> DeckSettingsContent(state, viewModel, onEditMode, contentModifier)
        }
    }
}

@Composable
private fun DeckSettingsContent(
    state: DeckSettingsUiState.Ready,
    viewModel: DeckSettingsViewModel,
    onEditMode: (String?) -> Unit,
    modifier: Modifier
) {
    val deck = state.deck
    // otwarte okno (i tryb, którego dotyczy) przeżywa obrót ekranu
    var showArchiveDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var modeToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var modeToReset by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        Section(stringResource(R.string.settings_section_general), Modifier.sharedDeckCard(deck.id)) {
            PersistedTextField(
                storedValue = deck.name,
                onValueChange = viewModel::rename,
                label = stringResource(R.string.settings_rename_label),
                isError = { it.isBlank() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Section(stringResource(R.string.settings_pages_config)) {
            for (pageIndex in 0 until deck.activePageCount) {
                PageRow(
                    pageIndex = pageIndex,
                    name = deck.pageNames.getOrElse(pageIndex) { "" },
                    language = deck.pageLanguages.getOrElse(pageIndex) { "" },
                    canMoveUp = pageIndex > 0,
                    canMoveDown = pageIndex < deck.activePageCount - 1,
                    onNameChange = { viewModel.setPageName(pageIndex, it) },
                    onLanguageChange = { viewModel.setPageLanguage(pageIndex, it) },
                    onMoveUp = { viewModel.swapPages(pageIndex, pageIndex - 1) },
                    onMoveDown = { viewModel.swapPages(pageIndex, pageIndex + 1) }
                )
            }
            PageCountStepper(deck.activePageCount, onChange = viewModel::setActivePageCount)
        }
        Section(stringResource(R.string.settings_study_scope)) {
            ChoiceChips(
                options = listOf(
                    StudyFilter.NEW_AND_REVIEW to R.string.filter_new_review,
                    StudyFilter.NEW to R.string.filter_new,
                    StudyFilter.REVIEW to R.string.filter_review,
                    StudyFilter.ALL to R.string.filter_all
                ),
                selected = deck.studyFilter,
                onSelect = viewModel::setStudyFilter
            )
            Text(stringResource(R.string.settings_card_order), style = MaterialTheme.typography.titleMedium)
            ChoiceChips(
                options = listOf(
                    CardOrder.SEQUENTIAL to R.string.settings_card_order_sequential,
                    CardOrder.RANDOM to R.string.settings_card_order_random,
                    CardOrder.HARDEST to R.string.settings_card_order_hardest
                ),
                selected = deck.cardOrder,
                onSelect = viewModel::setCardOrder
            )
        }
        ModesSection(
            activeModeId = state.activeMode.id,
            modes = state.modes,
            onSelect = viewModel::setActiveMode,
            onEdit = onEditMode,
            onReset = { modeToReset = it.id },
            onDelete = { modeToDelete = it.id }
        )
        OutlinedButton(onClick = {
            showArchiveDialog = true
        }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_archive_btn))
        }
        Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.btn_delete), modifier = Modifier.padding(start = Spacing.s))
        }
    }

    if (showArchiveDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_archive_title),
            text = pluralStringResource(
                R.plurals.dialog_archive_confirm,
                ArchiveConstants.RETENTION_DAYS,
                ArchiveConstants.RETENTION_DAYS
            ),
            confirmLabel = stringResource(R.string.btn_archive),
            onConfirm = {
                showArchiveDialog = false
                viewModel.archive()
            },
            onDismiss = { showArchiveDialog = false }
        )
    }
    if (showDeleteDialog) {
        DeleteDeckDialog(
            deckName = deck.name,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
    modeToDelete?.let { modeId ->
        ConfirmDialog(
            title = stringResource(R.string.mode_list_delete_title),
            text = stringResource(R.string.mode_list_delete_desc),
            confirmLabel = stringResource(R.string.btn_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteMode(modeId)
                modeToDelete = null
            },
            onDismiss = { modeToDelete = null }
        )
    }
    modeToReset?.let { modeId ->
        ConfirmDialog(
            title = stringResource(R.string.mode_list_reset_title),
            text = stringResource(R.string.mode_list_reset_desc),
            confirmLabel = stringResource(R.string.mode_list_reset),
            onConfirm = {
                viewModel.resetMode(modeId)
                modeToReset = null
            },
            onDismiss = { modeToReset = null }
        )
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    RayCollCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PageCountStepper(count: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.import_pages_count),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        FilledTonalIconButton(onClick = { onChange(count - 1) }, enabled = count > PageLimits.MIN_PAGE_COUNT) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.a11y_fewer_pages))
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.m)
        )
        FilledTonalIconButton(onClick = { onChange(count + 1) }, enabled = count < PageLimits.MAX_VISIBLE_PAGE_COUNT) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.a11y_more_pages))
        }
    }
}

@Composable
private fun <T> ChoiceChips(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = {
                onSelect(value)
            }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun ModesSection(
    activeModeId: String,
    modes: List<StudyMode>,
    onSelect: (String) -> Unit,
    onEdit: (String?) -> Unit,
    onReset: (StudyMode) -> Unit,
    onDelete: (StudyMode) -> Unit
) {
    RayCollCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.study_modes_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onEdit(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.mode_list_new), modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            modes.forEach { mode ->
                val active = activeModeId == mode.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.35f
                                )
                            } else {
                                Color.Transparent
                            },
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = active, onClick = { onSelect(mode.id) })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(mode.displayName(), style = MaterialTheme.typography.bodyLarge)
                        ModeBadges(mode)
                    }
                    ModeActions(
                        mode,
                        onEdit = { onEdit(mode.id) },
                        onReset = { onReset(mode) },
                        onDelete = { onDelete(mode) }
                    )
                }
            }
        }
    }
}
