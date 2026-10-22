package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundPause
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.defaultPageName
import com.raycoll.app.domain.seed.isBuiltInModeSourceId

// Sanityzacja edytora trybow i metadane stron.

/** Dopełnia nazwy i języki stron do [count]; istniejące metadane zostają. */
fun padPageMetadata(
    pageNames: List<String>,
    pageLanguages: List<String>,
    count: Int,
    defaultName: (Int) -> String = ::defaultPageName
): Pair<List<String>, List<String>> {
    val names = pageNames + (pageNames.size until count).map(defaultName)
    val languages = pageLanguages + List((count - pageLanguages.size).coerceAtLeast(0)) { DEFAULT_PAGE_LANGUAGE }
    return names to languages
}

private fun Int.asPageIndex(): Int = coerceAtLeast(0)

private fun Double.asMultiplier(): Double = coerceIn(0.0, StudyConstants.MAX_PAUSE_MULTIPLIER)

private fun Int.asThreshold(): Int = coerceIn(0, StudyConstants.MAX_THRESHOLD_PERCENT)

private fun Int.asRating(): Int = coerceIn(Ratings.MIN, Ratings.MAX)

private fun normalizeCompoundPause(pause: CompoundPause?): CompoundPause? = when (pause) {
    is CompoundPause.Fixed -> pause.copy(ms = pause.ms.coerceAtLeast(0))

    is CompoundPause.Dynamic -> pause.copy(
        page = pause.page.asPageIndex(),
        multiplier = pause.multiplier.asMultiplier()
    )

    null -> null
}

private fun normalizeBranch(branch: CompoundBranch): CompoundBranch = branch.copy(
    speakPage = branch.speakPage?.asPageIndex(),
    pause = normalizeCompoundPause(branch.pause),
    fixedRating = branch.fixedRating.asRating()
)

fun normalizeModeStep(step: ModeStep): ModeStep = when (step) {
    is CompoundStep -> step.copy(params = normalizeCompoundParams(step.params))

    is ShowPageStep -> step.copy(pageIndex = step.pageIndex.asPageIndex())

    is SpeakPageStep -> step.copy(pageIndex = step.pageIndex.asPageIndex())

    is DynamicPauseStep -> step.copy(
        nextPageIndex = step.nextPageIndex.asPageIndex(),
        pauseMultiplier = step.pauseMultiplier.asMultiplier()
    )

    is ListenAndCheckStep -> step.copy(
        pageIndex = step.pageIndex.asPageIndex(),
        successThreshold = step.successThreshold.asThreshold()
    )

    is AutoRateFixedStep -> step.copy(rating = step.rating.asRating())

    is WaitStep -> step.copy(ms = step.ms.coerceAtLeast(0))

    is ShowAllPagesStep, is WaitForTapToRevealNextStep, is WaitForTapToRevealStep, is ShowRatingsStep,
    is SpeakAllPagesStep, is FeedbackSuccessStep, is FeedbackErrorStep, is AutoRateFromAnswerStep,
    is MarkFailedStep, is WaitForTapStep, is NextCardStep -> step
}

private fun normalizeCompoundParams(params: CompoundParams): CompoundParams = when (params) {
    is CompoundParams.PresentFront -> params.copy(page = params.page.asPageIndex())

    is CompoundParams.SpeakPauseNext -> params.copy(
        page = params.page.asPageIndex(),
        nextPage = params.nextPage.asPageIndex(),
        multiplier = params.multiplier.asMultiplier()
    )

    is CompoundParams.AutoFlip -> params.copy(
        questionPage = params.questionPage.asPageIndex(),
        answerPage = params.answerPage.asPageIndex(),
        multiplier = params.multiplier.asMultiplier()
    )

    is CompoundParams.ListenGrade -> params.copy(
        answerPage = params.answerPage.asPageIndex(),
        threshold = params.threshold.asThreshold(),
        onCorrect = normalizeBranch(params.onCorrect),
        onWrong = normalizeBranch(params.onWrong)
    )

    is CompoundParams.GradeAfterListen -> params.copy(
        onCorrect = normalizeBranch(params.onCorrect),
        onWrong = normalizeBranch(params.onWrong)
    )

    is CompoundParams.AutoPassNext -> params.copy(rating = params.rating.asRating())

    is CompoundParams.FlipReveal, is CompoundParams.ShowAllGrade, is CompoundParams.FailNext -> params
}

fun normalizeStudyMode(mode: StudyMode): StudyMode {
    // tryb wbudowany tylko ze znanym źródłem — inaczej traktowany jak własny
    val sourceId = mode.builtInSourceId?.takeIf { mode.isBuiltIn && isBuiltInModeSourceId(it) }
    return mode.copy(
        steps = mode.steps.map(::normalizeModeStep),
        isBuiltIn = sourceId != null,
        builtInSourceId = sourceId
    )
}
