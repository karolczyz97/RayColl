package com.raycoll.app.ui.modedetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
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
import com.raycoll.app.domain.steps.ModeIssue
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun issueText(issue: ModeIssue): String = when (issue) {
    ModeIssue.NameRequired -> stringResource(R.string.mode_issue_name_required)

    ModeIssue.NoSteps -> stringResource(R.string.mode_issue_no_steps)

    is ModeIssue.PageOutOfRange -> stringResource(
        R.string.mode_issue_page_out_of_range,
        issue.stepNumber,
        issue.pageNumber
    )

    ModeIssue.NoTerminalStep -> stringResource(
        R.string.mode_issue_no_terminal,
        stringResource(R.string.step_show_ratings),
        stringResource(R.string.step_next_card)
    )

    ModeIssue.AutoRateWithoutStt -> stringResource(
        R.string.mode_issue_auto_rate_without_stt,
        stringResource(R.string.step_auto_rate_from_answer),
        stringResource(R.string.step_type_listen_and_check)
    )

    is ModeIssue.NoFallbackAfterListen -> stringResource(
        R.string.mode_issue_no_fallback,
        issue.stepNumber,
        stringResource(R.string.step_show_ratings),
        stringResource(R.string.step_next_card)
    )

    is ModeIssue.ConditionAfterTapGate -> stringResource(R.string.mode_issue_condition_after_tap, issue.stepNumber)
}

@Composable
fun stepTitle(step: ModeStep): String = when (step) {
    is ShowPageStep -> stringResource(R.string.step_show_page, step.pageIndex + 1)

    is ShowAllPagesStep -> stringResource(R.string.step_show_all_pages)

    is WaitForTapToRevealNextStep -> stringResource(R.string.step_wait_for_tap_to_reveal_next)

    is WaitForTapToRevealStep -> stringResource(R.string.step_wait_for_tap_to_reveal)

    is ShowRatingsStep -> stringResource(R.string.step_show_ratings)

    is SpeakPageStep -> stringResource(R.string.step_speak_page, step.pageIndex + 1)

    is SpeakAllPagesStep -> stringResource(R.string.step_speak_all_pages)

    is DynamicPauseStep -> stringResource(
        R.string.step_dynamic_pause,
        step.nextPageIndex + 1,
        formatMultiplier(step.pauseMultiplier, LocalConfiguration.current.locales[0])
    )

    is WaitStep -> stringResource(R.string.step_wait, step.ms)

    is ListenAndCheckStep -> stringResource(R.string.step_listen_and_check, step.pageIndex + 1, step.successThreshold)

    is FeedbackSuccessStep -> stringResource(R.string.step_feedback_success)

    is FeedbackErrorStep -> stringResource(R.string.step_feedback_error)

    is AutoRateFromAnswerStep -> stringResource(R.string.step_auto_rate_from_answer)

    is AutoRateFixedStep -> stringResource(R.string.step_auto_rate_fixed, step.rating)

    is MarkFailedStep -> stringResource(R.string.step_mark_failed)

    is WaitForTapStep -> stringResource(R.string.step_wait_for_tap)

    is NextCardStep -> stringResource(R.string.step_next_card)

    is CompoundStep -> stringResource(R.string.step_compound, compoundName(step.params))
}

internal fun formatMultiplier(value: Double, locale: Locale): String = DecimalNumberFormat.format(value)
    .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

@Composable
fun compoundName(params: CompoundParams): String = stringResource(
    when (params) {
        is CompoundParams.PresentFront -> R.string.compound_present_front_name
        is CompoundParams.FlipReveal -> R.string.compound_flip_reveal_name
        is CompoundParams.ShowAllGrade -> R.string.compound_show_all_grade_name
        is CompoundParams.SpeakPauseNext -> R.string.compound_speak_pause_next_name
        is CompoundParams.AutoFlip -> R.string.compound_auto_flip_name
        is CompoundParams.ListenGrade -> R.string.compound_listen_grade_name
        is CompoundParams.GradeAfterListen -> R.string.compound_grade_after_listen_name
        is CompoundParams.AutoPassNext -> R.string.compound_auto_pass_next_name
        is CompoundParams.FailNext -> R.string.compound_fail_next_name
    }
)
