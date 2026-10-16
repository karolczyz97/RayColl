package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.ui.components.ChoiceChip
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeDeleteDialog
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.PersistedTextField
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SectionCard
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.navigation.sharedDeckCard
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.Spacing

@Composable
fun DeckSettingsScreen(
    onBack: () -> Unit,
    onEditMode: (modeId: String?) -> Unit,
    viewModel: DeckSettingsViewModel,
    onClosed: (ScreenResult?) -> Unit = { onBack() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val deleteFailed = stringResource(R.string.settings_delete_error)
    val saveFailed = stringResource(R.string.errors_save_failed)

    CollectEvents(viewModel.events) { event ->
        when (event) {
            DeckSettingsEvent.Closed -> onClosed(null)
            is DeckSettingsEvent.Archived -> onClosed(ScreenResult.Archived(event.deckId))
            DeckSettingsEvent.DeleteFailed -> messages.showLatest(deleteFailed)
            DeckSettingsEvent.SaveFailed -> messages.showLatest(saveFailed)
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
    val resources = LocalResources.current
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
        SectionCard(Modifier.sharedDeckCard(deck.id), stringResource(R.string.settings_section_general)) {
            PersistedTextField(
                storedValue = deck.name,
                onValueChange = viewModel::rename,
                label = stringResource(R.string.settings_rename_label),
                isError = { it.isBlank() },
                errorText = stringResource(R.string.import_error_name_required),
                modifier = Modifier.fillMaxWidth()
            )
        }
        SectionCard(title = stringResource(R.string.settings_pages_config)) {
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
            PageCountStepper(deck.activePageCount, onChange = { count ->
                viewModel.setActivePageCount(count) { resources.getString(R.string.import_page_label, it + 1) }
            })
        }
        SectionCard(title = stringResource(R.string.settings_study_scope)) {
            Text(stringResource(R.string.settings_study_filter), style = MaterialTheme.typography.labelLarge)
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
            Text(stringResource(R.string.settings_card_order), style = MaterialTheme.typography.labelLarge)
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
        }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_archive_btn))
        }
        Button(
            onClick = { showDeleteDialog = true },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.btn_delete), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
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
        ModeDeleteDialog(
            onConfirm = {
                viewModel.deleteMode(modeId)
                modeToDelete = null
            },
            onDismiss = { modeToDelete = null }
        )
    }
    modeToReset?.let { modeId ->
        ModeResetDialog(
            onConfirm = {
                viewModel.resetMode(modeId)
                modeToReset = null
            },
            onDismiss = { modeToReset = null }
        )
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
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = { onChange(count - 1) },
            enabled =
                count > PageLimits.MIN_PAGE_COUNT
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.a11y_fewer_pages))
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.m)
        )
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = { onChange(count + 1) },
            enabled =
                count < PageLimits.MAX_VISIBLE_PAGE_COUNT
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.a11y_more_pages))
        }
    }
}

@Composable
private fun <T> ChoiceChips(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        options.forEach { (value, label) ->
            ChoiceChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}
