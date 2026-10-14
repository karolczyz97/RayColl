package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.withCondition
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.theme.Spacing

private const val MAX_WAIT_MS = 600_000
private val FIELD_WIDTH: Dp = 140.dp

/** Szerokość pola liczbowego rośnie z rozmiarem czcionki, żeby etykieta nie była ucinana. */
@Composable
private fun fieldWidth(): Dp = FIELD_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f)

/** Zgłasza, czy pole o danej etykiecie pokazuje teraz poprawną liczbę (blokada zapisu trybu). */
private val LocalFieldValidity = staticCompositionLocalOf<(Int, Boolean) -> Unit> { { _, _ -> } }

/** Jeden krok trybu: tytuł, przestawianie, warunek i parametry. */
@Composable
fun StepCard(
    number: Int,
    step: ModeStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (ModeStep) -> Unit,
    modifier: Modifier = Modifier,
    onFieldValidityChange: (field: Int, valid: Boolean) -> Unit = { _, _ -> }
) {
    // tytuł kroku w opisach przycisków, żeby czytnik ekranu odróżniał kolejne karty
    val title = stringResource(R.string.mode_editor_step_title, number, stepTitle(step))
    RayCollCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(
                            R.string.a11y_item_action,
                            stringResource(R.string.mode_editor_move_up),
                            title
                        )
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            R.string.a11y_item_action,
                            stringResource(R.string.mode_editor_move_down),
                            title
                        )
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.a11y_item_action,
                            stringResource(R.string.btn_delete),
                            title
                        ),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (step !is CompoundStep) ConditionSelector(step, onUpdate)
            CompositionLocalProvider(LocalFieldValidity provides onFieldValidityChange) {
                StepParams(step, onUpdate)
            }
        }
    }
}

@Composable
private fun ConditionSelector(step: ModeStep, onUpdate: (ModeStep) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        listOf(
            null to R.string.step_condition_always,
            StepCondition.CORRECT to R.string.step_condition_correct,
            StepCondition.WRONG to R.string.step_condition_wrong
        ).forEach { (condition, label) ->
            FilterChip(
                selected = step.condition == condition,
                onClick = { onUpdate(step.withCondition(condition)) },
                label = { Text(stringResource(label)) },
                // wybór jednej opcji, nie pole wyboru
                modifier = Modifier.semantics { role = Role.RadioButton }
            )
        }
    }
}

@Composable
private fun StepParams(step: ModeStep, onUpdate: (ModeStep) -> Unit) {
    when (step) {
        is ShowPageStep -> PageField(R.string.compound_field_page, step.pageIndex) {
            onUpdate(step.copy(pageIndex = it))
        }

        is SpeakPageStep -> PageField(R.string.compound_field_page, step.pageIndex) {
            onUpdate(step.copy(pageIndex = it))
        }

        is WaitStep -> IntField(R.string.compound_field_wait_ms, step.ms.toInt(), 0..MAX_WAIT_MS) {
            onUpdate(step.copy(ms = it.toLong()))
        }

        is DynamicPauseStep -> FieldRow {
            PageField(R.string.compound_field_page, step.nextPageIndex) { onUpdate(step.copy(nextPageIndex = it)) }
            MultiplierField(step.pauseMultiplier) { onUpdate(step.copy(pauseMultiplier = it)) }
        }

        is ListenAndCheckStep -> FieldRow {
            PageField(R.string.compound_field_page, step.pageIndex) { onUpdate(step.copy(pageIndex = it)) }
            ThresholdField(step.successThreshold) { onUpdate(step.copy(successThreshold = it)) }
        }

        is AutoRateFixedStep -> RatingField(step.rating) { onUpdate(step.copy(rating = it)) }

        is CompoundStep -> CompoundParams(step) { onUpdate(step.copy(params = it)) }

        else -> Unit
    }
}

@Composable
private fun CompoundParams(step: CompoundStep, onUpdate: (CompoundParams) -> Unit) {
    when (val params = step.params) {
        is CompoundParams.PresentFront -> FieldRow {
            PageField(R.string.compound_field_page, params.page) { onUpdate(params.copy(page = it)) }
            LabeledSwitch(R.string.compound_field_speak, params.speak) { onUpdate(params.copy(speak = it)) }
        }

        is CompoundParams.SpeakPauseNext -> FieldRow {
            PageField(R.string.compound_field_page, params.page) { onUpdate(params.copy(page = it)) }
            PageField(R.string.compound_field_next_page, params.nextPage) { onUpdate(params.copy(nextPage = it)) }
            MultiplierField(params.multiplier) { onUpdate(params.copy(multiplier = it)) }
        }

        is CompoundParams.AutoFlip -> FieldRow {
            PageField(R.string.compound_field_question_page, params.questionPage) {
                onUpdate(params.copy(questionPage = it))
            }
            PageField(R.string.compound_field_answer_page, params.answerPage) { onUpdate(params.copy(answerPage = it)) }
            MultiplierField(params.multiplier) { onUpdate(params.copy(multiplier = it)) }
            LabeledSwitch(R.string.compound_field_speak_question, params.speakQuestion) {
                onUpdate(params.copy(speakQuestion = it))
            }
            LabeledSwitch(R.string.compound_field_speak_answer, params.speakAnswer) {
                onUpdate(params.copy(speakAnswer = it))
            }
        }

        is CompoundParams.ListenGrade -> FieldRow {
            PageField(R.string.compound_field_answer_page, params.answerPage) { onUpdate(params.copy(answerPage = it)) }
            ThresholdField(params.threshold) { onUpdate(params.copy(threshold = it)) }
            LabeledSwitch(R.string.compound_field_manual_fallback, params.manualFallback) {
                onUpdate(params.copy(manualFallback = it))
            }
        }

        is CompoundParams.GradeAfterListen ->
            LabeledSwitch(R.string.compound_field_manual_fallback, params.manualFallback) {
                onUpdate(params.copy(manualFallback = it))
            }

        is CompoundParams.AutoPassNext -> RatingField(params.rating) { onUpdate(params.copy(rating = it)) }

        is CompoundParams.FlipReveal -> RevealStyleSelector(params.revealStyle) {
            onUpdate(params.copy(revealStyle = it))
        }

        is CompoundParams.ShowAllGrade, is CompoundParams.FailNext -> Unit
    }
}

@Composable
private fun RevealStyleSelector(selected: RevealStyle, onChange: (RevealStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.compound_field_reveal_style), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            listOf(
                RevealStyle.NEXT to R.string.compound_reveal_next,
                RevealStyle.ALL to R.string.compound_reveal_all
            ).forEach { (style, label) ->
                FilterChip(
                    selected = selected == style,
                    onClick = { onChange(style) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.semantics { role = Role.RadioButton }
                )
            }
        }
    }
}

@Composable
private fun FieldRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        itemVerticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** Numer strony pokazywany od 1, zapisywany jako indeks od 0; tyle stron, ile może mieć talia. */
@Composable
private fun PageField(labelRes: Int, pageIndex: Int, onChange: (Int) -> Unit) =
    IntField(labelRes, pageIndex + 1, 1..PageLimits.MAX_VISIBLE_PAGE_COUNT) { onChange(it - 1) }

@Composable
private fun ThresholdField(value: Int, onChange: (Int) -> Unit) = IntField(
    R.string.compound_field_threshold,
    value,
    0..100,
    onChange = onChange
)

@Composable
private fun RatingField(value: Int, onChange: (Int) -> Unit) {
    // nazwa oceny pod polem: „3 – Dobrze” mówi więcej niż sama liczba
    val names = listOf(
        R.string.study_rating_1,
        R.string.study_rating_2,
        R.string.study_rating_3,
        R.string.study_rating_4
    ).map { stringResource(it) }
    IntField(
        R.string.compound_field_rating,
        value,
        Ratings.MIN..Ratings.MAX,
        hint = { names.getOrNull(it - Ratings.MIN) },
        onChange = onChange
    )
}

@Composable
private fun IntField(
    labelRes: Int,
    value: Int,
    range: IntRange,
    hint: (Int) -> String? = { null },
    onChange: (Int) -> Unit
) {
    val onValidity = LocalFieldValidity.current
    NumberField(
        label = stringResource(labelRes),
        value = value,
        range = range,
        numberFormat = IntNumberFormat,
        onChange = onChange,
        onValidityChange = { onValidity(labelRes, it) },
        hint = hint,
        modifier = Modifier.width(fieldWidth())
    )
}

@Composable
private fun MultiplierField(value: Double, onChange: (Double) -> Unit) {
    val onValidity = LocalFieldValidity.current
    NumberField(
        label = stringResource(R.string.compound_field_multiplier),
        value = value,
        range = 0.0..StudyConstants.MAX_PAUSE_MULTIPLIER,
        numberFormat = DecimalNumberFormat,
        onChange = onChange,
        onValidityChange = { onValidity(R.string.compound_field_multiplier, it) },
        modifier = Modifier.width(fieldWidth())
    )
}

@Composable
private fun LabeledSwitch(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}
