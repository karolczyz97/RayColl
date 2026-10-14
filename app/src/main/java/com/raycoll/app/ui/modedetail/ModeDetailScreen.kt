package com.raycoll.app.ui.modedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.steps.ModeIssue
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.modeDisplayName
import com.raycoll.app.ui.theme.Spacing

@Composable
fun ModeDetailScreen(onBack: () -> Unit, viewModel: ModeDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddStep by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    // Pola liczbowe z błędem (klucz kroku + pole): zapis czeka, aż użytkownik je poprawi.
    val invalidFields = remember { mutableStateSetOf<String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.errors_save_failed)

    CollectEvents(viewModel.events) { event ->
        when (event) {
            ModeDetailEvent.Closed -> onBack()
            ModeDetailEvent.SaveFailed -> snackbarHostState.showSnackbar(saveFailed)
        }
    }

    val editing = uiState as? ModeDetailUiState.Editing
    ScreenScaffold(
        title = when {
            editing == null || editing.isNew -> stringResource(R.string.mode_editor_new_title)

            else -> modeDisplayName(editing.name, editing.builtIn).ifBlank {
                stringResource(R.string.route_study_mode_detail)
            }
        },
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (editing != null) {
                if (editing.builtIn != null) {
                    IconButton(onClick = { showResetConfirm = true }, enabled = editing.canReset) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.mode_list_reset))
                    }
                }
                TextButton(onClick = viewModel::save, enabled = editing.canSave && invalidFields.isEmpty()) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            ModeDetailUiState.Loading -> LoadingState(contentModifier)

            ModeDetailUiState.ChoosingTemplate -> TemplatePicker(contentModifier, onChoose = viewModel::chooseTemplate)

            is ModeDetailUiState.Editing -> ModeEditor(
                state = state,
                onNameChange = viewModel::setName,
                onAddStep = { showAddStep = true },
                onUpdateStep = viewModel::updateStep,
                onMoveStep = viewModel::moveStep,
                onRemoveStep = viewModel::removeStep,
                onFieldValidityChange = { field, valid ->
                    if (valid) invalidFields -= field else invalidFields += field
                },
                modifier = contentModifier.imePadding()
            )
        }
    }

    if (showAddStep) {
        AddStepDialog(
            onDismiss = { showAddStep = false },
            onChoose = {
                viewModel.addStep(it)
                showAddStep = false
            }
        )
    }
    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.mode_list_reset_title),
            text = stringResource(R.string.mode_list_reset_desc),
            confirmLabel = stringResource(R.string.mode_list_reset),
            onConfirm = {
                showResetConfirm = false
                viewModel.resetToBuiltIn()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}

@Composable
private fun ModeEditor(
    state: ModeDetailUiState.Editing,
    onNameChange: (String) -> Unit,
    onAddStep: () -> Unit,
    onUpdateStep: (Int, ModeStep) -> Unit,
    onMoveStep: (from: Int, to: Int) -> Unit,
    onRemoveStep: (Int) -> Unit,
    onFieldValidityChange: (field: String, valid: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                // pole pokazuje tłumaczenie nazwy wbudowanej, a zapisuje nazwę kanoniczną
                val translatedName = modeDisplayName(state.builtIn?.defaultName.orEmpty(), state.builtIn)
                OutlinedTextField(
                    value = modeDisplayName(state.name, state.builtIn),
                    onValueChange = { input ->
                        onNameChange(
                            if (state.builtIn != null && input == translatedName) state.builtIn.defaultName else input
                        )
                    },
                    label = { Text(stringResource(R.string.mode_editor_name_label)) },
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.builtIn != null) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            stringResource(R.string.mode_editor_builtin_badge),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs)
                        )
                    }
                }
                AnimatedVisibility(visible = state.issues.isNotEmpty()) { IssuesBanner(state.issues) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.mode_editor_steps_header, state.steps.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAddStep) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = stringResource(R.string.mode_editor_add_step),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        itemsIndexed(state.steps, key = { _, item -> item.key }) { index, item ->
            StepCard(
                number = index + 1,
                step = item.step,
                canMoveUp = index > 0,
                canMoveDown = index < state.steps.lastIndex,
                onMoveUp = { onMoveStep(index, index - 1) },
                onMoveDown = { onMoveStep(index, index + 1) },
                onDelete = { onRemoveStep(index) },
                onUpdate = { onUpdateStep(index, it) },
                onFieldValidityChange = { field, valid -> onFieldValidityChange("${item.key}/$field", valid) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun TemplatePicker(modifier: Modifier, onChoose: (ModeTemplate) -> Unit) {
    Column(modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.mode_editor_template_title), style = MaterialTheme.typography.titleMedium)
        val buttonModifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
        Button(onClick = {
            onChoose(ModeTemplate.CLASSIC)
        }, shape = MaterialTheme.shapes.small, modifier = buttonModifier) {
            Text(stringResource(R.string.mode_editor_template_classic))
        }
        Button(onClick = {
            onChoose(ModeTemplate.AUDIO)
        }, shape = MaterialTheme.shapes.small, modifier = buttonModifier) {
            Text(stringResource(R.string.mode_editor_template_audio))
        }
        OutlinedButton(onClick = {
            onChoose(ModeTemplate.BLANK)
        }, shape = MaterialTheme.shapes.small, modifier = buttonModifier) {
            Text(stringResource(R.string.mode_editor_template_blank))
        }
    }
}

@Composable
private fun IssuesBanner(issues: List<ModeIssue>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(stringResource(R.string.mode_editor_issues_title), style = MaterialTheme.typography.titleSmall)
            }
            issues.forEach { issue ->
                Text(stringResource(R.string.list_bullet, issueText(issue)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
