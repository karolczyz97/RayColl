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

    // Warunek na samym kroku compound NIE jest propagowany na kroki po ekspansji
    // (parytet z expandCompound w compoundSteps.ts). Propagacja rodzica na krok
    // listen_and_check + gałęzie correct/wrong tworzyła pułapkę: pominięty warunkiem
    // listen zostawiał stary lastAnswerResult, od którego odpalały się gałęzie.
    private fun expandCompound(step: CompoundStep): List<ModeStep> {
        val params = step.params
        return when (params) {
            is CompoundParams.PresentFront -> buildList {
                add(ShowPageStep(pageIndex = params.page))
                if (params.speak) add(SpeakPageStep(pageIndex = params.page))
            }
            is CompoundParams.FlipReveal -> when (params.revealStyle) {
                // fallback na "all" dla nieznanych wartości — jak normalizeCompoundParams
                "next" -> listOf(
                    WaitForTapToRevealNextStep(),
                    ShowRatingsStep(),
                )
                else -> listOf(
                    WaitForTapToRevealStep(),
                    ShowAllPagesStep(),
                    ShowRatingsStep(),
                )
            }
            is CompoundParams.ShowAllGrade -> listOf(
                ShowAllPagesStep(),
                ShowRatingsStep(),
            )
            is CompoundParams.SpeakPauseNext -> listOf(
                SpeakPageStep(pageIndex = params.page),
                DynamicPauseStep(nextPageIndex = params.nextPage, pauseMultiplier = params.multiplier),
                ShowPageStep(pageIndex = params.nextPage),
            )
            is CompoundParams.AutoFlip -> buildList {
                add(ShowPageStep(pageIndex = params.questionPage))
                if (params.speakQuestion) add(SpeakPageStep(pageIndex = params.questionPage))
                add(DynamicPauseStep(nextPageIndex = params.questionPage, pauseMultiplier = params.multiplier))
                add(ShowAllPagesStep())
                if (params.speakAnswer) add(SpeakPageStep(pageIndex = params.answerPage))
            }
            is CompoundParams.ListenGrade -> buildList {
                add(ListenAndCheckStep(pageIndex = params.answerPage, successThreshold = params.threshold))
                addAll(expandBranch(params.onCorrect, StepCondition.CORRECT))
                addAll(expandBranch(params.onWrong, StepCondition.WRONG))
                if (params.manualFallback) {
                    add(ShowAllPagesStep())
                    add(ShowRatingsStep())
                }
            }
            is CompoundParams.GradeAfterListen -> buildList {
                addAll(expandBranch(params.onCorrect, StepCondition.CORRECT))
                addAll(expandBranch(params.onWrong, StepCondition.WRONG))
                if (params.manualFallback) {
                    add(ShowAllPagesStep())
                    add(ShowRatingsStep())
                }
            }
            is CompoundParams.AutoPassNext -> listOf(
                AutoRateFixedStep(rating = params.rating),
                NextCardStep(),
            )
            is CompoundParams.FailNext -> listOf(
                MarkFailedStep(),
                AutoRateFixedStep(rating = 1),
                NextCardStep(),
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
