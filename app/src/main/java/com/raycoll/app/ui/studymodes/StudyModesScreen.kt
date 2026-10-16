package com.raycoll.app.ui.studymodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.raycoll.app.ui.components.Banner
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeActions
import com.raycoll.app.ui.components.ModeBadges
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.animateListItem
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.components.rememberModeDelete
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.settingsBackground
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
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val saveFailed = stringResource(R.string.errors_save_failed)
    CollectEvents(viewModel.events) { messages.showLatest(saveFailed) }
    val deleteMode = rememberModeDelete(messages, viewModel::deleteMode, viewModel::restoreMode)

    ScreenScaffold(
        title = stringResource(R.string.app_settings_study_modes),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        containerColor = settingsBackground,
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

            is StudyModesUiState.Ready -> LazyColumn(
                modifier = contentModifier,
                contentPadding = Spacing.screenContent(bottom = Spacing.fabClearance),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                item(key = "info") { InfoBanner(Modifier.padding(bottom = Spacing.item)) }
                itemsIndexed(state.modes, key = { _, mode -> mode.id }) { index, mode ->
                    ModeItem(
                        mode = mode,
                        shapes = ListItemDefaults.segmentedShapes(index, state.modes.size),
                        onEdit = { onEditMode(mode.id) },
                        onReset = { resetTarget = mode.id },
                        onDelete = { deleteMode(mode.id) },
                        modifier = Modifier.animateListItem(this)
                    )
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

/** Tryb jako pozycja listy w stylu ustawień: klik otwiera edytor, akcje po prawej. */
@Composable
private fun ModeItem(
    mode: StudyMode,
    shapes: ListItemShapes,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editLabel =
        stringResource(R.string.a11y_item_action, stringResource(R.string.mode_list_edit), mode.displayName())
    SegmentedListItem(
        onClick = onEdit,
        shapes = shapes,
        modifier = modifier.semantics { onClick(label = editLabel, action = null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ModeBadges(mode)
                Text(stringResource(R.string.study_modes_steps_count, mode.steps.size))
            }
        },
        trailingContent = { ModeActions(mode, onEdit = onEdit, onReset = onReset, onDelete = onDelete) }
    ) { Text(mode.displayName()) }
}
