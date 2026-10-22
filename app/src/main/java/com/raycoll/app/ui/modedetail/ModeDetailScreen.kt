package com.raycoll.app.ui.modedetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.steps.ModeIssue
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.ui.components.AppTextField
import com.raycoll.app.ui.components.Banner
import com.raycoll.app.ui.components.BuiltInBadge
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.ModeResetDialog
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SwitchRow
import com.raycoll.app.ui.components.animateListItem
import com.raycoll.app.ui.components.canonicalModeName
import com.raycoll.app.ui.components.descriptionRes
import com.raycoll.app.ui.components.expandVerticallyIn
import com.raycoll.app.ui.components.modeDisplayName
import com.raycoll.app.ui.components.moveActions
import com.raycoll.app.ui.components.nameRes
import com.raycoll.app.ui.components.rememberLazyReorderController
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.reorderItem
import com.raycoll.app.ui.components.reorderOnLongPress
import com.raycoll.app.ui.components.settingsItemColors
import com.raycoll.app.ui.components.shrinkVerticallyOut
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun ModeDetailScreen(onBack: () -> Unit, viewModel: ModeDetailViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expertMode by viewModel.expertMode.collectAsStateWithLifecycle()
    var showAddStep by rememberSaveable { mutableStateOf(false) }
    var addAfterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    // Pola liczbowe z błędem (klucz kroku + pole): zapis czeka, aż użytkownik je poprawi.
    val invalidFields = remember { mutableStateSetOf<String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.errors_save_failed)
    val stepDeleted = stringResource(R.string.mode_editor_step_deleted)
    val undoLabel = stringResource(R.string.mode_editor_undo)

    CollectEvents(viewModel.events) { event ->
        when (event) {
            ModeDetailEvent.Closed -> onBack()
            ModeDetailEvent.SaveFailed -> messages.showLatest(saveFailed)
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
        closeInsteadOfBack = true,
        snackbarHostState = snackbarHostState,
        actions = {
            if (editing != null) {
                if (editing.builtIn != null) {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                        showResetConfirm = true
                    }, enabled = editing.canReset) {
                        Icon(
                            painterResource(R.drawable.ic_restart_alt),
                            contentDescription = stringResource(R.string.mode_list_reset)
                        )
                    }
                }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = viewModel::save,
                    modifier = Modifier.padding(end = Spacing.s),
                    enabled =
                        editing.canSave && invalidFields.isEmpty()
                ) {
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
                expertMode = expertMode,
                onExpertModeChange = viewModel::setExpertMode,
                onNameChange = viewModel::setName,
                onAddStep = {
                    addAfterKey = null
                    showAddStep = true
                },
                onAddBelow = { key ->
                    addAfterKey = key
                    showAddStep = true
                },
                onUpdateStep = viewModel::updateStep,
                onMoveStep = viewModel::moveStep,
                onRemoveStep = { index ->
                    viewModel.removeStep(index)
                    // osobna korutyna: komunikat z cofnięciem nie może blokować zdarzeń z ViewModelu (np. zamknięcia)
                    scope.launch {
                        val result = messages.showAction(stepDeleted, actionLabel = undoLabel, key = "stepUndo")
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
            expert = expertMode,
            onDismiss = { showAddStep = false },
            onChoose = {
                viewModel.addStep(it, addAfterKey)
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
    expertMode: Boolean,
    onExpertModeChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onAddStep: () -> Unit,
    onAddBelow: (String) -> Unit,
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
    val shownBlocking = rememberLastIssues(blockingIssues)
    val shownTips = rememberLastIssues(tips)

    val listState = rememberLazyListState()
    ScrollToAddedStep(state, listState)
    val reorder = rememberLazyReorderController(listState, state.steps.map { it.key }, onMoveStep)
    val moveUpLabel = stringResource(R.string.mode_editor_move_up)
    val moveDownLabel = stringResource(R.string.mode_editor_move_down)

    Column(modifier) {
        // powody zablokowanego zapisu są przypięte nad listą, żeby było je widać przy każdym kroku
        AnimatedVisibility(
            visible = blockingIssues.isNotEmpty(),
            enter = expandVerticallyIn(),
            exit = shrinkVerticallyOut()
        ) {
            NoticeBanner(
                title = stringResource(R.string.mode_editor_issues_title),
                issues = shownBlocking,
                icon = painterResource(R.drawable.ic_warning),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s)
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = Spacing.screenContent(),
            verticalArrangement = Arrangement.spacedBy(Spacing.item)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    // pole pokazuje tłumaczenie nazwy wbudowanej, a zapisuje nazwę kanoniczną
                    val translatedName = modeDisplayName(state.builtIn?.defaultName.orEmpty(), state.builtIn)
                    var hadFocus by remember { mutableStateOf(false) }
                    AppTextField(
                        value = modeDisplayName(state.name, state.builtIn),
                        onValueChange = { input ->
                            nameTouched = true
                            onNameChange(canonicalModeName(input, state.builtIn, translatedName))
                        },
                        label = { Text(stringResource(R.string.mode_editor_name_label)) },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(R.string.mode_issue_name_required)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                if (hadFocus && !focus.isFocused) nameTouched = true
                                if (focus.isFocused) hadFocus = true
                            }
                    )
                    if (state.builtIn != null) BuiltInBadge()
                    AnimatedVisibility(
                        visible = tips.isNotEmpty(),
                        enter = expandVerticallyIn(),
                        exit = shrinkVerticallyOut()
                    ) {
                        NoticeBanner(
                            title = stringResource(R.string.mode_editor_tips_title),
                            issues = shownTips,
                            icon = painterResource(R.drawable.ic_info),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.mode_editor_expert_mode),
                        description = stringResource(R.string.mode_editor_expert_mode_desc),
                        checked = expertMode,
                        onChange = onExpertModeChange
                    )
                    Text(
                        stringResource(R.string.mode_editor_steps_header, state.steps.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }
            itemsIndexed(state.steps, key = { _, item -> item.key }) { index, item ->
                StepCard(
                    number = index + 1,
                    step = item.step,
                    onDelete = { onRemoveStep(index) },
                    onAddBelow = { onAddBelow(item.key) },
                    onUpdate = { onUpdateStep(index, it) },
                    onFieldValidityChange = { field, valid -> onFieldValidityChange("${item.key}/$field", valid) },
                    reorderActions = moveActions(index, state.steps.size, moveUpLabel, moveDownLabel, onMoveStep),
                    modifier = Modifier
                        .reorderItem(reorder, item.key)
                        .reorderOnLongPress(reorder, item.key)
                        // przeciągany krok porusza się pod palcem, więc nie może mieć animacji pozycji listy
                        .then(if (reorder.isActive(item.key)) Modifier else Modifier.animateListItem(this))
                )
            }
            item(key = "add_step") {
                OutlinedButton(
                    onClick = onAddStep,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Text(
                        stringResource(R.string.mode_editor_add_step),
                        Modifier.padding(start = ButtonDefaults.IconSpacing)
                    )
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
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(Spacing.screenContent()),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        Text(
            stringResource(R.string.mode_editor_template_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.s)
        )
        ModeTemplate.entries.forEachIndexed { index, template ->
            val builtIn = template.builtIn
            SegmentedListItem(
                onClick = { onChoose(template) },
                shapes = ListItemDefaults.segmentedShapes(index, ModeTemplate.entries.size),
                colors = settingsItemColors(),
                supportingContent = builtIn?.let { { Text(stringResource(it.descriptionRes())) } }
            ) { Text(stringResource(builtIn?.nameRes() ?: R.string.mode_editor_template_blank)) }
        }
    }
}

/** Ramka z listą uwag do trybu: czerwona dla błędów blokujących zapis, spokojniejsza dla wskazówek. */
@Composable
private fun NoticeBanner(
    title: String,
    issues: List<ModeIssue>,
    icon: Painter,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Banner(icon = icon, containerColor = container, contentColor = content, modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        // przypięta ramka nie może zająć połowy ekranu, gdy uwag jest dużo
        Column(Modifier.heightIn(max = 136.dp).verticalScroll(rememberScrollState())) {
            issues.forEach { issue ->
                Text(stringResource(R.string.list_bullet, issueText(issue)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Pusty wynik nie usuwa tekstu wychodzącej animacji; nowy wynik od razu jest widoczny. */
@Composable
private fun rememberLastIssues(issues: List<ModeIssue>): List<ModeIssue> {
    var last by remember { mutableStateOf(issues) }
    SideEffect { if (issues.isNotEmpty()) last = issues }
    return issues.ifEmpty { last }
}
