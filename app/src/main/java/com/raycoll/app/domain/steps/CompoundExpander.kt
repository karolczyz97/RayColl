package com.raycoll.app.domain.steps

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.BranchRating
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
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep

object CompoundExpander {

    fun expandModeSteps(steps: List<ModeStep>): List<ModeStep> = steps.flatMap(::expandStep)

    fun expandStep(step: ModeStep): List<ModeStep> = if (step is CompoundStep) expandCompound(step) else listOf(step)

    // Warunek samego kroku złożonego NIE przechodzi na kroki po rozwinięciu: pominięty
    // warunkiem nasłuch zostawiłby stary wynik odpowiedzi, od którego odpaliłyby się gałęzie.
    private fun expandCompound(step: CompoundStep): List<ModeStep> {
        val params = step.params
        return when (params) {
            is CompoundParams.PresentFront -> buildList {
                add(ShowPageStep(pageIndex = params.page))
                if (params.speak) add(SpeakPageStep(pageIndex = params.page))
            }

            is CompoundParams.FlipReveal -> when (params.revealStyle) {
                RevealStyle.NEXT -> listOf(
                    WaitForTapToRevealStep(),
                    ShowRatingsStep()
                )

                RevealStyle.ALL -> listOf(
                    WaitForTapStep(),
                    ShowAllPagesStep(),
                    ShowRatingsStep()
                )
            }

            is CompoundParams.ShowAllGrade -> listOf(
                ShowAllPagesStep(),
                ShowRatingsStep()
            )

            is CompoundParams.SpeakPauseNext -> listOf(
                SpeakPageStep(pageIndex = params.page),
                DynamicPauseStep(nextPageIndex = params.nextPage, pauseMultiplier = params.multiplier),
                ShowPageStep(pageIndex = params.nextPage)
            )

            is CompoundParams.AutoFlip -> buildList {
                add(ShowPageStep(pageIndex = params.questionPage))
                if (params.speakQuestion) add(SpeakPageStep(pageIndex = params.questionPage))
                add(DynamicPauseStep(nextPageIndex = params.questionPage, pauseMultiplier = params.multiplier))
                add(ShowAllPagesStep())
                if (params.speakAnswer) add(SpeakPageStep(pageIndex = params.answerPage))
            }

            is CompoundParams.ListenGrade -> {
                val listen = ListenAndCheckStep(pageIndex = params.answerPage, successThreshold = params.threshold)
                listOf<ModeStep>(listen) + gradeSteps(params.onCorrect, params.onWrong, params.manualFallback)
            }

            is CompoundParams.GradeAfterListen -> gradeSteps(params.onCorrect, params.onWrong, params.manualFallback)

            is CompoundParams.AutoPassNext -> listOf(
                AutoRateFixedStep(rating = params.rating),
                NextCardStep()
            )

            is CompoundParams.FailNext -> listOf(
                MarkFailedStep(),
                AutoRateFixedStep(rating = Ratings.AGAIN),
                NextCardStep()
            )
        }
    }

    /** Gałęzie po nasłuchu; bez nich (pominięty nasłuch, błąd mikrofonu) opcjonalnie ocena ręczna. */
    private fun gradeSteps(
        onCorrect: CompoundBranch,
        onWrong: CompoundBranch,
        manualFallback: Boolean
    ): List<ModeStep> = buildList {
        addAll(expandBranch(onCorrect, StepCondition.CORRECT))
        addAll(expandBranch(onWrong, StepCondition.WRONG))
        if (manualFallback) {
            add(ShowAllPagesStep())
            add(ShowRatingsStep())
        }
    }

    private fun expandBranch(branch: CompoundBranch, condition: StepCondition): List<ModeStep> = buildList {
        if (branch.feedback) {
            add(
                when (condition) {
                    StepCondition.CORRECT -> FeedbackSuccessStep(condition = condition)
                    StepCondition.WRONG -> FeedbackErrorStep(condition = condition)
                }
            )
        }
        if (branch.revealAll) add(ShowAllPagesStep(condition = condition))
        if (branch.speakPage != null) add(SpeakPageStep(condition = condition, pageIndex = branch.speakPage))
        if (branch.markFailed) add(MarkFailedStep(condition = condition))
        when (branch.rate) {
            BranchRating.FROM_ANSWER -> add(AutoRateFromAnswerStep(condition = condition))
            BranchRating.FIXED -> add(AutoRateFixedStep(condition = condition, rating = branch.fixedRating))
            BranchRating.NONE -> Unit
        }
        when (val pause = branch.pause) {
            is CompoundPause.Fixed -> add(WaitStep(condition = condition, ms = pause.ms))

            is CompoundPause.Dynamic -> add(
                DynamicPauseStep(condition = condition, nextPageIndex = pause.page, pauseMultiplier = pause.multiplier)
            )

            null -> {}
        }
        if (branch.advance) add(NextCardStep(condition = condition))
    }
}
