package com.raycoll.app.ui.studymodes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.isModifiedBuiltIn
import com.raycoll.app.ui.components.Banner
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeResetAction
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.ModeSupportingContent
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SettingsGroupTitle
import com.raycoll.app.ui.components.SwipeToDeleteItem
import com.raycoll.app.ui.components.animateListItem
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.components.rememberModeDelete
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.settingsItemColors
import com.raycoll.app.ui.theme.Spacing

@Composable
fun StudyModesScreen(
    onBack: () -> Unit,
    onEditMode: (modeId: String) -> Unit,
    onNewMode: () -> Unit,
    viewModel: StudyModesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var resetTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredModeId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val saveFailed = stringResource(R.string.errors_save_failed)
    CollectEvents(viewModel.events) {
        messages.cancelAction("modeUndo")
        messages.showLatest(saveFailed)
    }
    val deleteMode = rememberModeDelete(messages, viewModel::deleteMode) { modeId ->
        restoredModeId = modeId
        viewModel.restoreMode(modeId)
    }

    ScreenScaffold(
        title = stringResource(R.string.app_settings_study_modes),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMode,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.mode_list_new))
            }
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            StudyModesUiState.Loading -> LoadingState(contentModifier)

            is StudyModesUiState.Ready -> {
                val (builtIn, own) = state.modes.partition { it.isBuiltIn }
                val modeItem: @Composable LazyItemScope.(ListItemShapes, StudyMode) -> Unit = { shapes, mode ->
                    ModeItem(
                        mode = mode,
                        shapes = shapes,
                        onEdit = { onEditMode(mode.id) },
                        onReset = { resetTarget = mode.id },
                        onDelete = { deleteMode(mode.id) },
                        enterFromStart = restoredModeId == mode.id,
                        onEntered = { if (restoredModeId == mode.id) restoredModeId = null },
                        modifier = Modifier.animateListItem(this)
                    )
                }
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = Spacing.screenContent(bottom = Spacing.fabClearance),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    item(key = "info") { InfoBanner() }
                    modeGroup(R.string.study_modes_section_builtin, builtIn, modeItem)
                    modeGroup(R.string.study_modes_section_own, own, modeItem)
                }
            }
        }
    }

    resetTarget?.let { modeId ->
        ModeResetDialog(
            onConfirm = {
                viewModel.resetMode(modeId)
                resetTarget = null
            },
            onDismiss = { resetTarget = null }
        )
    }
}

/** Grupa trybów pod własnym nagłówkiem; pusta grupa nie pokazuje nic. */
private fun LazyListScope.modeGroup(
    @StringRes title: Int,
    modes: List<StudyMode>,
    modeItem: @Composable LazyItemScope.(ListItemShapes, StudyMode) -> Unit
) {
    if (modes.isEmpty()) return
    item(key = "title-$title") {
        // przerwa nad nagłówkiem jak między grupami ustawień, mimo wąskiej przerwy między pozycjami listy
        SettingsGroupTitle(stringResource(title), Modifier.padding(top = Spacing.item - ListItemDefaults.SegmentedGap))
    }
    itemsIndexed(modes, key = { _, mode -> mode.id }) { index, mode ->
        modeItem(ListItemDefaults.segmentedShapes(index, modes.size), mode)
    }
}

@Composable
private fun InfoBanner(modifier: Modifier = Modifier) {
    Banner(
        modifier = modifier,
        icon = painterResource(R.drawable.ic_info),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(stringResource(R.string.study_modes_info))
    }
}

/** Klik otwiera edytor; własny tryb usuwa się tym samym gestem co fiszki. */
@Composable
private fun ModeItem(
    mode: StudyMode,
    shapes: ListItemShapes,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    enterFromStart: Boolean,
    onEntered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editLabel =
        stringResource(R.string.a11y_item_action, stringResource(R.string.mode_list_edit), mode.displayName())
    val content: @Composable () -> Unit = {
        SegmentedListItem(
            onClick = onEdit,
            shapes = shapes,
            colors = settingsItemColors(),
            modifier = Modifier.semantics { onClick(label = editLabel, action = null) },
            supportingContent = {
                ModeSupportingContent(mode) { Text(stringResource(R.string.study_modes_steps_count, mode.steps.size)) }
            },
            trailingContent = if (mode.isModifiedBuiltIn()) {
                { ModeResetAction(mode, onReset) }
            } else {
                null
            }
        ) { Text(mode.displayName()) }
    }
    if (mode.isBuiltIn) {
        Box(modifier) { content() }
    } else {
        SwipeToDeleteItem(
            onDelete = onDelete,
            deleteLabel = stringResource(
                R.string.a11y_item_action,
                stringResource(R.string.btn_delete),
                mode.displayName()
            ),
            shape = shapes.shape,
            modifier = modifier,
            enterFromStart = enterFromStart,
            onEntered = onEntered,
            content = content
        )
    }
}
