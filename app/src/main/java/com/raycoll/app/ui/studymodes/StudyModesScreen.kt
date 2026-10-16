package com.raycoll.app.ui.studymodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeActions
import com.raycoll.app.ui.components.ModeBadges
import com.raycoll.app.ui.components.ModeDeleteDialog
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.components.rememberSnackbarMessages
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
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val saveFailed = stringResource(R.string.errors_save_failed)
    CollectEvents(viewModel.events) { messages.showLatest(saveFailed) }

    ScreenScaffold(
        title = stringResource(R.string.app_settings_study_modes),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onNewMode) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.mode_list_new))
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
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                item(key = "info") { InfoBanner() }
                items(state.modes, key = { it.id }) { mode ->
                    ModeCard(
                        mode = mode,
                        onEdit = { onEditMode(mode.id) },
                        onReset = { resetTarget = mode.id },
                        onDelete = { deleteTarget = mode.id },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "new") {
                    OutlinedButton(
                        onClick = onNewMode,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Text(
                            stringResource(R.string.mode_list_new),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                        )
                    }
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
    deleteTarget?.let { modeId ->
        ModeDeleteDialog(
            onConfirm = {
                viewModel.deleteMode(modeId)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun InfoBanner() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.study_modes_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: StudyMode,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editLabel =
        stringResource(R.string.a11y_item_action, stringResource(R.string.mode_list_edit), mode.displayName())
    RayCollCard(
        onClick = onEdit,
        onClickLabel = editLabel,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(mode.displayName(), style = MaterialTheme.typography.titleMedium)
                ModeBadges(mode)
                Text(
                    stringResource(R.string.study_modes_steps_count, mode.steps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ModeActions(mode, onEdit = onEdit, onReset = onReset, onDelete = onDelete)
        }
    }
}
