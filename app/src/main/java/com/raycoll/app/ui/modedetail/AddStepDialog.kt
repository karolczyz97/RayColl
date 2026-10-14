package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raycoll.app.R
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
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
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.ui.theme.Spacing

private class StepChoice(val labelRes: Int, val create: () -> ModeStep)

private class StepGroup(val labelRes: Int, val choices: List<StepChoice>)

/**
 * Kroki do wyboru, pogrupowane jak w edytorze; nowy krok dostaje wartości domyślne.
 * Gotowe bloki są pierwsze, bo to z nich najczęściej składa się tryb.
 */
private val STEP_CATALOG = listOf(
    StepGroup(
        R.string.step_category_compound,
        listOf(
            StepChoice(R.string.compound_present_front_name) { CompoundStep(params = CompoundParams.PresentFront()) },
            StepChoice(R.string.compound_flip_reveal_name) { CompoundStep(params = CompoundParams.FlipReveal()) },
            StepChoice(R.string.compound_show_all_grade_name) { CompoundStep(params = CompoundParams.ShowAllGrade) },
            StepChoice(R.string.compound_speak_pause_next_name) {
                CompoundStep(params = CompoundParams.SpeakPauseNext())
            },
            StepChoice(R.string.compound_auto_flip_name) { CompoundStep(params = CompoundParams.AutoFlip()) },
            StepChoice(R.string.compound_listen_grade_name) { CompoundStep(params = CompoundParams.ListenGrade()) },
            StepChoice(R.string.compound_grade_after_listen_name) {
                CompoundStep(params = CompoundParams.GradeAfterListen())
            },
            StepChoice(R.string.compound_auto_pass_next_name) { CompoundStep(params = CompoundParams.AutoPassNext()) },
            StepChoice(R.string.compound_fail_next_name) { CompoundStep(params = CompoundParams.FailNext) }
        )
    ),
    StepGroup(
        R.string.step_category_display,
        listOf(
            StepChoice(R.string.step_type_show_page) { ShowPageStep() },
            StepChoice(R.string.step_show_all_pages) { ShowAllPagesStep() },
            StepChoice(R.string.step_wait_for_tap_to_reveal_next) { WaitForTapToRevealNextStep() },
            StepChoice(R.string.step_wait_for_tap_to_reveal) { WaitForTapToRevealStep() }
        )
    ),
    StepGroup(
        R.string.step_category_audio,
        listOf(
            StepChoice(R.string.step_type_speak_page) { SpeakPageStep() },
            StepChoice(R.string.step_speak_all_pages) { SpeakAllPagesStep() },
            StepChoice(R.string.step_type_listen_and_check) { ListenAndCheckStep(pageIndex = 1) },
            StepChoice(R.string.step_feedback_success) { FeedbackSuccessStep() },
            StepChoice(R.string.step_feedback_error) { FeedbackErrorStep() }
        )
    ),
    StepGroup(
        R.string.step_category_timing,
        listOf(
            StepChoice(R.string.step_type_wait) { WaitStep() },
            StepChoice(R.string.step_type_dynamic_pause) { DynamicPauseStep() },
            StepChoice(R.string.step_wait_for_tap) { WaitForTapStep() }
        )
    ),
    StepGroup(
        R.string.step_category_rating,
        listOf(
            StepChoice(R.string.step_show_ratings) { ShowRatingsStep() },
            StepChoice(R.string.step_auto_rate_from_answer) { AutoRateFromAnswerStep() },
            StepChoice(R.string.step_type_auto_rate_fixed) { AutoRateFixedStep() },
            StepChoice(R.string.step_mark_failed) { MarkFailedStep() }
        )
    ),
    StepGroup(R.string.step_category_flow, listOf(StepChoice(R.string.step_next_card) { NextCardStep() }))
)

@Composable
fun AddStepDialog(onDismiss: () -> Unit, onChoose: (ModeStep) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_editor_add_step)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                STEP_CATALOG.forEach { group ->
                    Text(
                        stringResource(group.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.s)
                    )
                    group.choices.forEach { choice ->
                        TextButton(onClick = { onChoose(choice.create()) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(choice.labelRes), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
