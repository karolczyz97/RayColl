package com.raycoll.app.ui.decksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.NameEditorDialog
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SettingsAction
import com.raycoll.app.ui.components.SettingsBlock
import com.raycoll.app.ui.components.SettingsGroup
import com.raycoll.app.ui.components.SettingsRadio
import com.raycoll.app.ui.components.SnackbarMessages
import com.raycoll.app.ui.components.moveActions
import com.raycoll.app.ui.components.rememberColumnReorder
import com.raycoll.app.ui.components.rememberModeDelete
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.reorderActions
import com.raycoll.app.ui.components.reorderItem
import com.raycoll.app.ui.components.reorderOnLongPress
import com.raycoll.app.ui.navigation.ScreenResult
import com.raycoll.app.ui.theme.Spacing

private const val DECK_NAME = -1

@Composable
fun DeckSettingsScreen(
    onBack: () -> Unit,
    onEditMode: (modeId: String?) -> Unit,
    viewModel: DeckSettingsViewModel,
    onClosed: (ScreenResult?) -> Unit = { onBack() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savingName by viewModel.savingName.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val deleteFailed = stringResource(R.string.settings_delete_error)
    val saveFailed = stringResource(R.string.errors_save_failed)
    var nameSaveAttempt by rememberSaveable { mutableIntStateOf(0) }
    var nameToEdit by rememberSaveable { mutableStateOf<Int?>(null) }

    CollectEvents(viewModel.events) { event ->
        when (event) {
            DeckSettingsEvent.Closed -> onClosed(null)

            is DeckSettingsEvent.Archived -> onClosed(ScreenResult.Archived(event.deckId))

            DeckSettingsEvent.DeleteFailed -> messages.showLatest(deleteFailed)

            DeckSettingsEvent.SaveFailed -> {
                messages.cancelAction("modeUndo")
                nameSaveAttempt++
                messages.showLatest(saveFailed)
            }

            is DeckSettingsEvent.NameSaved -> {
                if (nameToEdit == (event.pageIndex ?: DECK_NAME)) nameToEdit = null
            }
        }
    }

    val ready = uiState as? DeckSettingsUiState.Ready
    ScreenScaffold(
        title = ready?.deck?.name ?: stringResource(R.string.route_deck_settings),
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            DeckSettingsUiState.Loading -> LoadingState(contentModifier)

            DeckSettingsUiState.DeckMissing -> ErrorState(stringResource(R.string.errors_deck_missing), contentModifier)

            is DeckSettingsUiState.Ready -> DeckSettingsContent(
                state,
                viewModel,
                messages,
                onEditMode,
                { nameToEdit = it },
                nameSaveAttempt,
                contentModifier
            )
        }
    }
    val target = nameToEdit
    if (ready != null && target != null && (target == DECK_NAME || target in 0 until ready.deck.activePageCount)) {
        NameEditorDialog(
            title = if (target == DECK_NAME) {
                stringResource(R.string.settings_rename_label)
            } else {
                stringResource(R.string.page_default_name, target + 1)
            },
            draftKey = "${ready.deck.id}:$target",
            initialValue = if (target == DECK_NAME) ready.deck.name else ready.deck.pageNames.getOrElse(target) { "" },
            saveAttempt = nameSaveAttempt,
            saving = savingName,
            allowBlank = target != DECK_NAME,
            onSave = { name ->
                if (target == DECK_NAME) viewModel.rename(name) else viewModel.setPageName(target, name)
            },
            onDismiss = { nameToEdit = null }
        )
    }
}

@Composable
private fun DeckSettingsContent(
    state: DeckSettingsUiState.Ready,
    viewModel: DeckSettingsViewModel,
    messages: SnackbarMessages,
    onEditMode: (String?) -> Unit,
    onEditName: (Int) -> Unit,
    failedSaves: Int,
    modifier: Modifier
) {
    val deck = state.deck
    val resources = LocalResources.current
    // otwarte okno (i tryb, którego dotyczy) przeżywa obrót ekranu
    var showArchiveDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val deleteMode = rememberModeDelete(messages, viewModel::deleteMode, viewModel::restoreMode)
    var modeToReset by rememberSaveable { mutableStateOf<String?>(null) }
    val pageOrder = rememberColumnReorder(
        keys = List(deck.activePageCount) { it },
        // nieudany zapis cofa pokazaną kolejność do danych
        version = listOf(deck.updatedAt, deck.pageNames, deck.pageLanguages, failedSaves),
        onCommit = viewModel::movePage
    )
    val moveUpLabel = stringResource(R.string.a11y_move_page_up)
    val moveDownLabel = stringResource(R.string.a11y_move_page_down)

    Column(
        modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenContent()),
        verticalArrangement = Arrangement.spacedBy(Spacing.item)
    ) {
        SettingsGroup(stringResource(R.string.settings_section_general)) {
            item { shapes ->
                SettingsAction(
                    shapes = shapes,
                    title = stringResource(R.string.settings_rename_label),
                    description = deck.name,
                    icon = painterResource(R.drawable.ic_edit),
                    onClick = { onEditName(DECK_NAME) }
                )
            }
        }
        SettingsGroup(stringResource(R.string.settings_pages_config)) {
            item { shapes ->
                SettingsBlock(shapes) {
                    PageCountStepper(deck.activePageCount, onChange = { count ->
                        viewModel.setActivePageCount(count) { resources.getString(R.string.page_default_name, it + 1) }
                    })
                }
            }
            // wiersz zachowuje swój numer strony z danych (key), choć w trakcie przeciągania zmienia miejsce
            pageOrder.order.forEachIndexed { position, page ->
                val pageIndex = page as Int
                item(key = "page-$pageIndex") { shapes ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .reorderItem(pageOrder.controller, pageIndex, shapes.shape)
                            .reorderOnLongPress(pageOrder.controller, pageIndex)
                            .reorderActions(
                                moveActions(
                                    position,
                                    deck.activePageCount,
                                    moveUpLabel,
                                    moveDownLabel,
                                    viewModel::movePage
                                )
                            )
                    ) {
                        PageRow(
                            shapes = shapes,
                            pageIndex = position,
                            name = deck.pageNames.getOrElse(pageIndex) { "" },
                            language = deck.pageLanguages.getOrElse(pageIndex) { "" },
                            onEditName = { onEditName(pageIndex) },
                            onLanguageChange = { viewModel.setPageLanguage(pageIndex, it) }
                        )
                    }
                }
            }
        }
        RadioGroup(
            title = stringResource(R.string.settings_study_filter),
            options = listOf(
                StudyFilter.NEW_AND_REVIEW to R.string.filter_new_review,
                StudyFilter.NEW to R.string.filter_new,
                StudyFilter.REVIEW to R.string.filter_review,
                StudyFilter.ALL to R.string.filter_all
            ),
            selected = deck.studyFilter,
            onSelect = viewModel::setStudyFilter
        )
        RadioGroup(
            title = stringResource(R.string.settings_card_order),
            options = listOf(
                CardOrder.SEQUENTIAL to R.string.settings_card_order_sequential,
                CardOrder.RANDOM to R.string.settings_card_order_random,
                CardOrder.HARDEST to R.string.settings_card_order_hardest
            ),
            selected = deck.cardOrder,
            onSelect = viewModel::setCardOrder
        )
        ModesSection(
            activeModeId = state.activeMode.id,
            modes = state.modes,
            onSelect = viewModel::setActiveMode,
            onEdit = onEditMode,
            onReset = { modeToReset = it.id },
            onDelete = { deleteMode(it.id) }
        )
        SettingsGroup(title = null) {
            item { shapes ->
                SettingsAction(
                    shapes = shapes,
                    title = stringResource(R.string.settings_archive_btn),
                    icon = painterResource(R.drawable.ic_archive),
                    onClick = { showArchiveDialog = true }
                )
            }
            item { shapes ->
                SettingsAction(
                    shapes = shapes,
                    title = stringResource(R.string.btn_delete),
                    icon = painterResource(R.drawable.ic_delete_forever),
                    destructive = true,
                    onClick = { showDeleteDialog = true }
                )
            }
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
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.a11y_fewer_pages))
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
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.a11y_more_pages))
        }
    }
}

/** Wybór jednej opcji jako lista z przyciskami radiowymi (jak w ustawieniach systemu). */
@Composable
private fun <T> RadioGroup(title: String, options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SettingsGroup(title) {
        options.forEach { (value, label) ->
            item { shapes ->
                SettingsRadio(shapes = shapes, selected = selected == value, onClick = { onSelect(value) }) {
                    Text(stringResource(label))
                }
            }
        }
    }
}
