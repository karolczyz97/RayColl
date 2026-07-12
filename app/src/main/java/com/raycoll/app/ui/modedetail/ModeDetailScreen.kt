package com.raycoll.app.ui.modedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.steps.ModeIssue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeDetailScreen(modeId: String?, onBack: () -> Unit, viewModel: ModeDetailViewModel = hiltViewModel()) {
    LaunchedEffect(modeId) { viewModel.init(modeId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) stringResource(R.string.mode_editor_new_title)
                        else state.name.ifBlank { stringResource(R.string.route_study_mode_detail) }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }, enabled = state.saveEnabled) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.templateChosen -> TemplatePicker(Modifier.padding(padding), onChoose = { viewModel.chooseTemplate(it) })
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.setName(it) },
                    label = { Text(stringResource(R.string.mode_editor_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (state.isBuiltIn) {
                    Text(
                        stringResource(R.string.mode_editor_builtin_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                AnimatedVisibility(visible = state.issues.isNotEmpty()) {
                    IssuesBanner(state.issues)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.mode_editor_steps_header, state.steps.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = stringResource(R.string.mode_editor_add_step), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.steps) { index, step ->
                        StepCard(
                            index = index,
                            step = step,
                            stepsCount = state.steps.size,
                            onMoveUp = { viewModel.moveStep(index, -1) },
                            onMoveDown = { viewModel.moveStep(index, +1) },
                            onDelete = { viewModel.removeStep(index) },
                            onUpdate = { viewModel.updateStep(index, it) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStepDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { viewModel.addStep(it); showAddDialog = false },
        )
    }
}

@Composable
private fun TemplatePicker(modifier: Modifier = Modifier, onChoose: (ModeTemplate) -> Unit) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.mode_editor_template_title), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { onChoose(ModeTemplate.CLASSIC) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_editor_template_classic))
        }
        Button(onClick = { onChoose(ModeTemplate.AUDIO) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_editor_template_audio))
        }
        Button(onClick = { onChoose(ModeTemplate.BLANK) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_editor_template_blank))
        }
    }
}

@Composable
private fun IssuesBanner(issues: List<ModeIssue>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(R.string.mode_editor_issues_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            issues.forEach { issue ->
                Text(
                    "• " + issueText(issue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun issueText(issue: ModeIssue): String = when (issue) {
    is ModeIssue.NameRequired -> stringResource(R.string.mode_issue_name_required)
    is ModeIssue.NoSteps -> stringResource(R.string.mode_issue_no_steps)
    is ModeIssue.PageOutOfRange -> stringResource(R.string.mode_issue_page_out_of_range, issue.stepNumber, issue.pageNumber)
    is ModeIssue.NoTerminalStep -> stringResource(R.string.mode_issue_no_terminal)
    is ModeIssue.AutoRateWithoutStt -> stringResource(R.string.mode_issue_auto_rate_without_stt)
}

@Composable
private fun stepTitle(step: ModeStep): String = when (step) {
    is ShowPageStep -> stringResource(R.string.step_show_page, step.pageIndex + 1)
    is ShowAllPagesStep -> stringResource(R.string.step_show_all_pages)
    is WaitForTapToRevealNextStep -> stringResource(R.string.step_wait_for_tap_to_reveal_next)
    is WaitForTapToRevealStep -> stringResource(R.string.step_wait_for_tap_to_reveal)
    is ShowRatingsStep -> stringResource(R.string.step_show_ratings)
    is SpeakPageStep -> stringResource(R.string.step_speak_page, step.pageIndex + 1)
    is SpeakAllPagesStep -> stringResource(R.string.step_speak_all_pages)
    is DynamicPauseStep -> stringResource(R.string.step_dynamic_pause, step.nextPageIndex + 1, step.pauseMultiplier.toInt())
    is WaitStep -> stringResource(R.string.step_wait, step.ms)
    is ListenAndCheckStep -> stringResource(R.string.step_listen_and_check, step.pageIndex + 1, step.successThreshold)
    is FeedbackSuccessStep -> stringResource(R.string.step_feedback_success)
    is FeedbackErrorStep -> stringResource(R.string.step_feedback_error)
    is AutoRateFromAnswerStep -> stringResource(R.string.step_auto_rate_from_answer)
    is AutoRateFixedStep -> stringResource(R.string.step_auto_rate_fixed, step.rating)
    is MarkFailedStep -> stringResource(R.string.step_mark_failed)
    is WaitForTapStep -> stringResource(R.string.step_wait_for_tap)
    is NextCardStep -> stringResource(R.string.step_next_card)
    is CompoundStep -> stringResource(R.string.step_compound, compoundKindName(step.params))
}

@Composable
private fun compoundKindName(params: CompoundParams): String = when (params) {
    is CompoundParams.PresentFront -> stringResource(R.string.compound_present_front_name)
    is CompoundParams.FlipReveal -> stringResource(R.string.compound_flip_reveal_name)
    is CompoundParams.ShowAllGrade -> stringResource(R.string.compound_show_all_grade_name)
    is CompoundParams.SpeakPauseNext -> stringResource(R.string.compound_speak_pause_next_name)
    is CompoundParams.AutoFlip -> stringResource(R.string.compound_auto_flip_name)
    is CompoundParams.ListenGrade -> stringResource(R.string.compound_listen_grade_name)
    is CompoundParams.GradeAfterListen -> stringResource(R.string.compound_grade_after_listen_name)
    is CompoundParams.AutoPassNext -> stringResource(R.string.compound_auto_pass_next_name)
    is CompoundParams.FailNext -> stringResource(R.string.compound_fail_next_name)
}

@Composable
private fun StepCard(
    index: Int,
    step: ModeStep,
    stepsCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (ModeStep) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${stepTitle(step)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.mode_editor_move_up))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < stepsCount - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.mode_editor_move_down))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            ConditionSelector(step = step, onUpdate = onUpdate)
            StepParamsEditor(step = step, onUpdate = onUpdate)
        }
    }
}

@Composable
private fun ConditionSelector(step: ModeStep, onUpdate: (ModeStep) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            null to R.string.step_condition_always,
            StepCondition.CORRECT to R.string.step_condition_correct,
            StepCondition.WRONG to R.string.step_condition_wrong,
        ).forEach { (condition, label) ->
            FilterChip(
                selected = step.condition == condition,
                onClick = { onUpdate(withCondition(step, condition)) },
                label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

private fun withCondition(step: ModeStep, condition: StepCondition?): ModeStep = when (step) {
    is ShowPageStep -> step.copy(condition = condition)
    is ShowAllPagesStep -> step.copy(condition = condition)
    is WaitForTapToRevealNextStep -> step.copy(condition = condition)
    is WaitForTapToRevealStep -> step.copy(condition = condition)
    is ShowRatingsStep -> step.copy(condition = condition)
    is SpeakPageStep -> step.copy(condition = condition)
    is SpeakAllPagesStep -> step.copy(condition = condition)
    is DynamicPauseStep -> step.copy(condition = condition)
    is WaitStep -> step.copy(condition = condition)
    is ListenAndCheckStep -> step.copy(condition = condition)
    is FeedbackSuccessStep -> step.copy(condition = condition)
    is FeedbackErrorStep -> step.copy(condition = condition)
    is AutoRateFromAnswerStep -> step.copy(condition = condition)
    is AutoRateFixedStep -> step.copy(condition = condition)
    is MarkFailedStep -> step.copy(condition = condition)
    is WaitForTapStep -> step.copy(condition = condition)
    is NextCardStep -> step.copy(condition = condition)
    is CompoundStep -> step.copy(condition = condition)
}

@Composable
private fun StepParamsEditor(step: ModeStep, onUpdate: (ModeStep) -> Unit) {
    when (step) {
        is ShowPageStep -> NumberField(R.string.compound_field_page, step.pageIndex + 1, 1, 20) { onUpdate(step.copy(pageIndex = it - 1)) }
        is SpeakPageStep -> NumberField(R.string.compound_field_page, step.pageIndex + 1, 1, 20) { onUpdate(step.copy(pageIndex = it - 1)) }
        is WaitStep -> NumberField(R.string.compound_field_wait_ms, step.ms.toInt(), 0, 600_000) { onUpdate(step.copy(ms = it.toLong())) }
        is DynamicPauseStep -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(R.string.compound_field_page, step.nextPageIndex + 1, 1, 20) { onUpdate(step.copy(nextPageIndex = it - 1)) }
            DoubleField(R.string.compound_field_multiplier, step.pauseMultiplier, 0.0, StudyConstants.MAX_PAUSE_MULTIPLIER.toDouble()) { onUpdate(step.copy(pauseMultiplier = it)) }
        }
        is ListenAndCheckStep -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(R.string.compound_field_page, step.pageIndex + 1, 1, 20) { onUpdate(step.copy(pageIndex = it - 1)) }
            NumberField(R.string.compound_field_threshold, step.successThreshold, 0, 100) { onUpdate(step.copy(successThreshold = it)) }
        }
        is AutoRateFixedStep -> NumberField(R.string.compound_field_rating, step.rating, 1, 4) { onUpdate(step.copy(rating = it)) }
        is CompoundStep -> CompoundParamsEditor(step, onUpdate)
        else -> Unit
    }
}

@Composable
private fun CompoundParamsEditor(step: CompoundStep, onUpdate: (ModeStep) -> Unit) {
    when (val p = step.params) {
        is CompoundParams.PresentFront -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(R.string.compound_field_page, p.page + 1, 1, 20) { onUpdate(step.copy(params = p.copy(page = it - 1))) }
            LabeledSwitch(R.string.compound_field_speak, p.speak) { onUpdate(step.copy(params = p.copy(speak = it))) }
        }
        is CompoundParams.SpeakPauseNext -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(R.string.compound_field_page, p.page + 1, 1, 20) { onUpdate(step.copy(params = p.copy(page = it - 1))) }
            NumberField(R.string.compound_field_next_page, p.nextPage + 1, 1, 20) { onUpdate(step.copy(params = p.copy(nextPage = it - 1))) }
            DoubleField(R.string.compound_field_multiplier, p.multiplier, 0.0, StudyConstants.MAX_PAUSE_MULTIPLIER.toDouble()) { onUpdate(step.copy(params = p.copy(multiplier = it))) }
        }
        is CompoundParams.AutoFlip -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(R.string.compound_field_question_page, p.questionPage + 1, 1, 20) { onUpdate(step.copy(params = p.copy(questionPage = it - 1))) }
                NumberField(R.string.compound_field_answer_page, p.answerPage + 1, 1, 20) { onUpdate(step.copy(params = p.copy(answerPage = it - 1))) }
                DoubleField(R.string.compound_field_multiplier, p.multiplier, 0.0, StudyConstants.MAX_PAUSE_MULTIPLIER.toDouble()) { onUpdate(step.copy(params = p.copy(multiplier = it))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledSwitch(R.string.compound_field_speak_question, p.speakQuestion) { onUpdate(step.copy(params = p.copy(speakQuestion = it))) }
                LabeledSwitch(R.string.compound_field_speak_answer, p.speakAnswer) { onUpdate(step.copy(params = p.copy(speakAnswer = it))) }
            }
        }
        is CompoundParams.ListenGrade -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(R.string.compound_field_answer_page, p.answerPage + 1, 1, 20) { onUpdate(step.copy(params = p.copy(answerPage = it - 1))) }
            NumberField(R.string.compound_field_threshold, p.threshold, 0, 100) { onUpdate(step.copy(params = p.copy(threshold = it))) }
            LabeledSwitch(R.string.compound_field_manual_fallback, p.manualFallback) { onUpdate(step.copy(params = p.copy(manualFallback = it))) }
        }
        is CompoundParams.GradeAfterListen -> LabeledSwitch(R.string.compound_field_manual_fallback, p.manualFallback) { onUpdate(step.copy(params = p.copy(manualFallback = it))) }
        is CompoundParams.AutoPassNext -> NumberField(R.string.compound_field_rating, p.rating, 1, 4) { onUpdate(step.copy(params = p.copy(rating = it))) }
        is CompoundParams.FlipReveal, is CompoundParams.ShowAllGrade, is CompoundParams.FailNext -> Unit
    }
}

@Composable
private fun NumberField(labelRes: Int, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
        },
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.width(110.dp),
        singleLine = true,
    )
}

@Composable
private fun DoubleField(labelRes: Int, value: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.replace(',', '.').toDoubleOrNull()?.let { onChange(it.coerceIn(min, max)) }
        },
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.width(110.dp),
        singleLine = true,
    )
}

@Composable
private fun LabeledSwitch(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class StepChoice(val labelRes: Int, val make: () -> ModeStep)
private data class StepCategory(val labelRes: Int, val choices: List<StepChoice>)

private fun stepCatalog(): List<StepCategory> = listOf(
    StepCategory(R.string.step_category_display, listOf(
        StepChoice(R.string.step_type_show_page) { ShowPageStep(pageIndex = 0) },
        StepChoice(R.string.step_show_all_pages) { ShowAllPagesStep() },
        StepChoice(R.string.step_wait_for_tap_to_reveal_next) { WaitForTapToRevealNextStep() },
        StepChoice(R.string.step_wait_for_tap_to_reveal) { WaitForTapToRevealStep() },
    )),
    StepCategory(R.string.step_category_audio, listOf(
        StepChoice(R.string.step_type_speak_page) { SpeakPageStep(pageIndex = 0) },
        StepChoice(R.string.step_speak_all_pages) { SpeakAllPagesStep() },
        StepChoice(R.string.step_type_listen_and_check) { ListenAndCheckStep(pageIndex = 1, successThreshold = StudyConstants.STEP_DEFAULT_THRESHOLD) },
        StepChoice(R.string.step_feedback_success) { FeedbackSuccessStep() },
        StepChoice(R.string.step_feedback_error) { FeedbackErrorStep() },
    )),
    StepCategory(R.string.step_category_timing, listOf(
        StepChoice(R.string.step_type_wait) { WaitStep(ms = StudyConstants.STEP_DEFAULT_WAIT_MS.toLong()) },
        StepChoice(R.string.step_type_dynamic_pause) { DynamicPauseStep(nextPageIndex = 0, pauseMultiplier = 1.0) },
        StepChoice(R.string.step_wait_for_tap) { WaitForTapStep() },
    )),
    StepCategory(R.string.step_category_rating, listOf(
        StepChoice(R.string.step_show_ratings) { ShowRatingsStep() },
        StepChoice(R.string.step_auto_rate_from_answer) { AutoRateFromAnswerStep() },
        StepChoice(R.string.step_type_auto_rate_fixed) { AutoRateFixedStep(rating = StudyConstants.STEP_DEFAULT_RATING) },
        StepChoice(R.string.step_mark_failed) { MarkFailedStep() },
    )),
    StepCategory(R.string.step_category_flow, listOf(
        StepChoice(R.string.step_next_card) { NextCardStep() },
    )),
    StepCategory(R.string.step_category_compound, listOf(
        StepChoice(R.string.compound_present_front_name) { CompoundStep(params = CompoundParams.PresentFront()) },
        StepChoice(R.string.compound_flip_reveal_name) { CompoundStep(params = CompoundParams.FlipReveal()) },
        StepChoice(R.string.compound_show_all_grade_name) { CompoundStep(params = CompoundParams.ShowAllGrade()) },
        StepChoice(R.string.compound_speak_pause_next_name) { CompoundStep(params = CompoundParams.SpeakPauseNext()) },
        StepChoice(R.string.compound_auto_flip_name) { CompoundStep(params = CompoundParams.AutoFlip()) },
        StepChoice(R.string.compound_listen_grade_name) { CompoundStep(params = CompoundParams.ListenGrade()) },
        StepChoice(R.string.compound_grade_after_listen_name) { CompoundStep(params = CompoundParams.GradeAfterListen(onWrong = CompoundBranch(rate = "fixed", fixedRating = 1, markFailed = true))) },
        StepChoice(R.string.compound_auto_pass_next_name) { CompoundStep(params = CompoundParams.AutoPassNext()) },
        StepChoice(R.string.compound_fail_next_name) { CompoundStep(params = CompoundParams.FailNext()) },
    )),
)

@Composable
private fun AddStepDialog(onDismiss: () -> Unit, onConfirm: (ModeStep) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_editor_add_step)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stepCatalog().forEach { category ->
                    Text(
                        stringResource(category.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    category.choices.forEach { choice ->
                        TextButton(onClick = { onConfirm(choice.make()) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(choice.labelRes), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
    )
}
