package com.raycoll.app.ui.modedetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.steps.ModeIssue
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.ui.components.BuiltInBadge
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.modeDisplayName
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun ModeDetailScreen(onBack: () -> Unit, viewModel: ModeDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddStep by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    // Pola liczbowe z błędem (klucz kroku + pole): zapis czeka, aż użytkownik je poprawi.
    val invalidFields = remember { mutableStateSetOf<String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.errors_save_failed)
    val stepDeleted = stringResource(R.string.mode_editor_step_deleted)
    val undoLabel = stringResource(R.string.mode_editor_undo)

    CollectEvents(viewModel.events) { event ->
        when (event) {
            ModeDetailEvent.Closed -> onBack()
            ModeDetailEvent.SaveFailed -> snackbarHostState.showSnackbar(saveFailed)
        }
    }

    val editing = uiState as? ModeDetailUiState.Editing
    // wyjście z edytora z niezapisanymi zmianami wymaga potwierdzenia (zapis zamyka ekran bez pytania)
    val requestBack = { if (editing?.dirty == true) showDiscardConfirm = true else onBack() }
    BackHandler(enabled = editing?.dirty == true) { showDiscardConfirm = true }

    ScreenScaffold(
        title = when {
            editing == null || editing.isNew -> stringResource(R.string.mode_editor_new_title)

            else -> modeDisplayName(editing.name, editing.builtIn).ifBlank {
                stringResource(R.string.route_study_mode_detail)
            }
        },
        onBack = requestBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (editing != null) {
                if (editing.builtIn != null) {
                    IconButton(onClick = { showResetConfirm = true }, enabled = editing.canReset) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.mode_list_reset))
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
                onRemoveStep = { index ->
                    viewModel.removeStep(index)
                    // osobna korutyna: komunikat z cofnięciem nie może blokować zdarzeń z ViewModelu (np. zamknięcia)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(stepDeleted, actionLabel = undoLabel)
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
                    }
                },
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
        ModeResetDialog(
            onConfirm = {
                showResetConfirm = false
                viewModel.resetToBuiltIn()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
    if (showDiscardConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.mode_editor_discard_title),
            text = stringResource(R.string.mode_editor_discard_desc),
            confirmLabel = stringResource(R.string.mode_editor_discard_confirm),
            destructive = true,
            onConfirm = {
                showDiscardConfirm = false
                onBack()
            },
            onDismiss = { showDiscardConfirm = false }
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
    // nowy tryb nie pokazuje błędu nazwy, zanim użytkownik cokolwiek wpisał
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    val nameError = state.name.isBlank() && (!state.isNew || nameTouched)
    val issues = state.issues.filterNot { it == ModeIssue.NameRequired && !nameError }
    val blockingIssues = issues.filter { it.blocking }
    val tips = issues.filterNot { it.blocking }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    ScrollToAddedStep(state, listState)
    val spacingPx = with(LocalDensity.current) { Spacing.s.toPx() }

    // Przesunięty krok zostaje pod palcem: lista przewija się o wysokość kroku, z którym się zamienił.
    fun moveStep(from: Int, to: Int) {
        val neighbourKey = state.steps[to].key
        val neighbourHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == neighbourKey }?.size
        onMoveStep(from, to)
        if (neighbourHeight != null) {
            val delta = (neighbourHeight + spacingPx) * if (to < from) -1 else 1
            scope.launch { listState.animateScrollBy(delta) }
        }
    }

    Column(modifier) {
        // powody zablokowanego zapisu są przypięte nad listą, żeby było je widać przy każdym kroku
        AnimatedVisibility(visible = blockingIssues.isNotEmpty()) {
            NoticeBanner(
                title = stringResource(R.string.mode_editor_issues_title),
                issues = blockingIssues,
                icon = Icons.Filled.Warning,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s)
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    // pole pokazuje tłumaczenie nazwy wbudowanej, a zapisuje nazwę kanoniczną
                    val translatedName = modeDisplayName(state.builtIn?.defaultName.orEmpty(), state.builtIn)
                    var hadFocus by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = modeDisplayName(state.name, state.builtIn),
                        onValueChange = { input ->
                            nameTouched = true
                            onNameChange(
                                if (state.builtIn != null &&
                                    input == translatedName
                                ) {
                                    state.builtIn.defaultName
                                } else {
                                    input
                                }
                            )
                        },
                        label = { Text(stringResource(R.string.mode_editor_name_label)) },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(R.string.mode_issue_name_required)) }
                        } else {
                            null
                        },
                        shape = MaterialTheme.shapes.small,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                if (hadFocus && !focus.isFocused) nameTouched = true
                                if (focus.isFocused) hadFocus = true
                            }
                    )
                    if (state.builtIn != null) BuiltInBadge()
                    AnimatedVisibility(visible = tips.isNotEmpty()) {
                        NoticeBanner(
                            title = stringResource(R.string.mode_editor_tips_title),
                            issues = tips,
                            icon = Icons.Filled.Info,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        stringResource(R.string.mode_editor_steps_header, state.steps.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            itemsIndexed(state.steps, key = { _, item -> item.key }) { index, item ->
                StepCard(
                    number = index + 1,
                    step = item.step,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.steps.lastIndex,
                    onMoveUp = { moveStep(index, index - 1) },
                    onMoveDown = { moveStep(index, index + 1) },
                    onDelete = { onRemoveStep(index) },
                    onUpdate = { onUpdateStep(index, it) },
                    onFieldValidityChange = { field, valid -> onFieldValidityChange("${item.key}/$field", valid) },
                    modifier = Modifier.animateItem()
                )
            }
            item(key = "add_step") {
                OutlinedButton(
                    onClick = onAddStep,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.mode_editor_add_step), Modifier.padding(start = Spacing.s))
                }
            }
        }
    }
}

/** Po dodaniu (albo cofnięciu usunięcia) kroku lista pokazuje go, zamiast zostawiać użytkownika na górze. */
@Composable
private fun ScrollToAddedStep(state: ModeDetailUiState.Editing, listState: LazyListState) {
    var knownKeys by remember { mutableStateOf(state.steps.map { it.key }.toSet()) }
    LaunchedEffect(state.steps) {
        val keys = state.steps.map { it.key }.toSet()
        val added = state.steps.indexOfFirst { it.key !in knownKeys }
        knownKeys = keys
        // pozycja 0 to nagłówek, kroki zaczynają się od 1
        if (added >= 0) listState.animateScrollToItem(added + 1)
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

/** Ramka z listą uwag do trybu: czerwona dla błędów blokujących zapis, spokojniejsza dla wskazówek. */
@Composable
private fun NoticeBanner(
    title: String,
    issues: List<ModeIssue>,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth()
    ) {
        // przypięta ramka nie może zająć połowy ekranu, gdy uwag jest dużo
        Column(
            Modifier
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                Icon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            issues.forEach { issue ->
                Text(stringResource(R.string.list_bullet, issueText(issue)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
