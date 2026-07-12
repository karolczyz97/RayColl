package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundPause
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
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep

object CompoundExpander {

    fun expandModeSteps(steps: List<ModeStep>): List<ModeStep> = steps.flatMap(::expandStep)

    fun expandStep(step: ModeStep): List<ModeStep> = if (step is CompoundStep) expandCompound(step) else listOf(step)

    private fun expandCompound(step: CompoundStep): List<ModeStep> {
        val params = step.params
        return when (params) {
            is CompoundParams.PresentFront -> buildList {
                add(ShowPageStep(condition = step.condition, pageIndex = params.page))
                if (params.speak) add(SpeakPageStep(condition = step.condition, pageIndex = params.page))
            }
            is CompoundParams.FlipReveal -> when (params.revealStyle) {
                "all" -> listOf(
                    WaitForTapToRevealStep(condition = step.condition),
                    ShowAllPagesStep(condition = step.condition),
                    ShowRatingsStep(condition = step.condition),
                )
                else -> listOf(
                    WaitForTapToRevealNextStep(condition = step.condition),
                    ShowRatingsStep(condition = step.condition),
                )
            }
            is CompoundParams.ShowAllGrade -> listOf(
                ShowAllPagesStep(condition = step.condition),
                ShowRatingsStep(condition = step.condition),
            )
            is CompoundParams.SpeakPauseNext -> listOf(
                SpeakPageStep(condition = step.condition, pageIndex = params.page),
                DynamicPauseStep(condition = step.condition, nextPageIndex = params.nextPage, pauseMultiplier = params.multiplier),
                ShowPageStep(condition = step.condition, pageIndex = params.nextPage),
            )
            is CompoundParams.AutoFlip -> buildList {
                add(ShowPageStep(condition = step.condition, pageIndex = params.questionPage))
                if (params.speakQuestion) add(SpeakPageStep(condition = step.condition, pageIndex = params.questionPage))
                add(DynamicPauseStep(condition = step.condition, nextPageIndex = params.questionPage, pauseMultiplier = params.multiplier))
                add(ShowAllPagesStep(condition = step.condition))
                if (params.speakAnswer) add(SpeakPageStep(condition = step.condition, pageIndex = params.answerPage))
            }
            is CompoundParams.ListenGrade -> buildList {
                add(ListenAndCheckStep(condition = step.condition, pageIndex = params.answerPage, successThreshold = params.threshold))
                addAll(expandBranch(params.onCorrect, StepCondition.CORRECT))
                addAll(expandBranch(params.onWrong, StepCondition.WRONG))
                if (params.manualFallback) {
                    add(ShowAllPagesStep(condition = step.condition))
                    add(ShowRatingsStep(condition = step.condition))
                }
            }
            is CompoundParams.GradeAfterListen -> buildList {
                addAll(expandBranch(params.onCorrect, StepCondition.CORRECT))
                addAll(expandBranch(params.onWrong, StepCondition.WRONG))
                if (params.manualFallback) {
                    add(ShowAllPagesStep(condition = step.condition))
                    add(ShowRatingsStep(condition = step.condition))
                }
            }
            is CompoundParams.AutoPassNext -> listOf(
                AutoRateFixedStep(condition = step.condition, rating = params.rating),
                NextCardStep(condition = step.condition),
            )
            is CompoundParams.FailNext -> listOf(
                MarkFailedStep(condition = step.condition),
                AutoRateFixedStep(condition = step.condition, rating = 1),
                NextCardStep(condition = step.condition),
            )
        }
    }

    private fun expandBranch(branch: CompoundBranch, condition: StepCondition): List<ModeStep> = buildList {
        if (branch.feedback) {
            add(if (condition == StepCondition.CORRECT) FeedbackSuccessStep(condition = condition) else FeedbackErrorStep(condition = condition))
        }
        if (branch.speakPage != null) add(SpeakPageStep(condition = condition, pageIndex = branch.speakPage))
        when (val pause = branch.pause) {
            is CompoundPause.Fixed -> add(WaitStep(condition = condition, ms = pause.ms))
            is CompoundPause.Dynamic -> add(DynamicPauseStep(condition = condition, nextPageIndex = pause.page, pauseMultiplier = pause.multiplier))
            null -> {}
        }
        if (branch.revealAll) add(ShowAllPagesStep(condition = condition))
        if (branch.markFailed) add(MarkFailedStep(condition = condition))
        when (branch.rate) {
            "fromAnswer" -> add(AutoRateFromAnswerStep(condition = condition))
            "fixed" -> add(AutoRateFixedStep(condition = condition, rating = branch.fixedRating))
        }
        if (branch.advance) add(NextCardStep(condition = condition))
    }
}
