package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundPause
import com.raycoll.app.domain.srs.isBuiltInModeSourceId

private val VALID_SRS_STATES = setOf(0, 1, 2, 3)
private val VALID_STUDY_FILTERS = setOf("all", "new", "review", "new+review")
private val VALID_CARD_ORDERS = setOf("sequential", "random", "hardest")

const val DEFAULT_STUDY_FILTER = "new+review"
const val DEFAULT_CARD_ORDER = "sequential"

fun normalizeStudyFilter(filter: String?): String =
    if (VALID_STUDY_FILTERS.contains(filter)) filter!! else DEFAULT_STUDY_FILTER

fun normalizeCardOrder(value: String?): String =
    if (VALID_CARD_ORDERS.contains(value)) value!! else DEFAULT_CARD_ORDER

fun padPageMetadata(
    pageNames: List<String>,
    pageLanguages: List<String>,
    count: Int,
): Pair<List<String>, List<String>> {
    val names = pageNames.toMutableList()
    val langs = pageLanguages.toMutableList()
    while (names.size < count) names.add("Page ${names.size + 1}")
    while (langs.size < count) langs.add("en-US")
    return Pair(names, langs)
}

fun getStoredPageCount(group: FlashcardGroup, pageNames: List<String>, pageLanguages: List<String>): Int {
    val maxCardPages = group.cards.maxOfOrNull { it.pages.size } ?: 0
    val rawStored = maxOf(pageNames.size, pageLanguages.size, maxCardPages, PageLimits.MIN_PAGE_COUNT)
    return minOf(rawStored, PageLimits.MAX_STORED_PAGE_COUNT)
}

private fun normalizeCard(card: Flashcard): Flashcard {
    val validState = if (VALID_SRS_STATES.contains(card.srsState.state)) card.srsState
        else card.srsState.copy(state = 0)
    return card.copy(srsState = validState)
}

fun normalizeGroup(group: FlashcardGroup): FlashcardGroup {
    val rawNames = group.pageNames
    val rawLanguages = group.pageLanguages
    val storedPageCount = getStoredPageCount(group, rawNames, rawLanguages)
    val (pageNames, pageLanguages) = padPageMetadata(rawNames, rawLanguages, storedPageCount)
    val maxVisible = minOf(PageLimits.MAX_VISIBLE_PAGE_COUNT, storedPageCount)
    val candidate = group.activePageCount
    val activePageCount = PageLimits.clampActivePageCount(candidate, maxVisible)

    return group.copy(
        activePageCount = activePageCount,
        pageNames = pageNames,
        pageLanguages = pageLanguages,
        cards = group.cards.map(::normalizeCard),
        studyFilter = normalizeStudyFilter(group.studyFilter),
        cardOrder = normalizeCardOrder(group.cardOrder),
    )
}

private fun normalizePageIndex(value: Int?): Int =
    if (value != null && value >= 0) value else 0

private fun clampMultiplier(value: Double?): Double =
    (value ?: 1.0).coerceIn(0.0, StudyConstants.MAX_PAUSE_MULTIPLIER.toDouble())

private fun clampThreshold(value: Int?): Int =
    (value ?: StudyConstants.STEP_DEFAULT_THRESHOLD).coerceIn(0, 100)

private fun clampRating(value: Int?): Int =
    (value ?: StudyConstants.STEP_DEFAULT_RATING).coerceIn(1, 4)

private fun clampMs(value: Long?): Long =
    (value ?: StudyConstants.STEP_DEFAULT_WAIT_MS.toLong()).coerceAtLeast(0)

private fun normalizeStepCondition(condition: StepCondition?): StepCondition? =
    if (condition == StepCondition.CORRECT || condition == StepCondition.WRONG) condition else null

private fun normalizeRate(rate: String, fallback: String): String =
    if (rate == "fromAnswer" || rate == "fixed" || rate == "none") rate else fallback

private fun normalizeCompoundPause(pause: CompoundPause?): CompoundPause? = when (pause) {
    is CompoundPause.Fixed -> pause.copy(ms = pause.ms.coerceAtLeast(0))
    is CompoundPause.Dynamic -> pause.copy(page = normalizePageIndex(pause.page), multiplier = clampMultiplier(pause.multiplier))
    null -> null
}

// Parytet z normalizeCompoundBranch (compoundSteps.ts): clampy pól gałęzi, żeby
// uszkodzony/ręcznie edytowany backup nie wniósł np. speakPage<0 ani rating>4 do
// ekspansji kroków. rate spoza {fromAnswer,fixed,none} → default gałęzi.
private fun normalizeBranch(branch: CompoundBranch, condition: StepCondition): CompoundBranch {
    val fallbackRate = if (condition == StepCondition.CORRECT) "fromAnswer" else "fixed"
    return branch.copy(
        speakPage = branch.speakPage?.let { normalizePageIndex(it) },
        pause = normalizeCompoundPause(branch.pause),
        rate = normalizeRate(branch.rate, fallbackRate),
        fixedRating = clampRating(branch.fixedRating),
    )
}

fun normalizeModeStep(step: ModeStep): ModeStep? {
    return when (step) {
        is CompoundStep -> normalizeCompoundStep(step)
        is com.raycoll.app.domain.model.ShowPageStep -> step.copy(
            condition = normalizeStepCondition(step.condition),
            pageIndex = normalizePageIndex(step.pageIndex)
        )
        is com.raycoll.app.domain.model.SpeakPageStep -> step.copy(
            condition = normalizeStepCondition(step.condition),
            pageIndex = normalizePageIndex(step.pageIndex)
        )
        is com.raycoll.app.domain.model.DynamicPauseStep -> step.copy(
            condition = normalizeStepCondition(step.condition),
            nextPageIndex = normalizePageIndex(step.nextPageIndex),
            pauseMultiplier = clampMultiplier(step.pauseMultiplier)
        )
        is com.raycoll.app.domain.model.ListenAndCheckStep -> step.copy(
            condition = normalizeStepCondition(step.condition),
            pageIndex = normalizePageIndex(step.pageIndex),
            successThreshold = clampThreshold(step.successThreshold)
        )
        is com.raycoll.app.domain.model.AutoRateFixedStep -> step.copy(
            condition = normalizeStepCondition(step.condition),
            rating = clampRating(step.rating)
        )
        is com.raycoll.app.domain.model.WaitStep -> step.copy(
            condition = normalizeStepCondition(step.condition),
            ms = clampMs(step.ms)
        )
        is com.raycoll.app.domain.model.ShowAllPagesStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.WaitForTapToRevealNextStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.WaitForTapToRevealStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.ShowRatingsStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.SpeakAllPagesStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.FeedbackSuccessStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.FeedbackErrorStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.AutoRateFromAnswerStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.MarkFailedStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.WaitForTapStep -> step.copy(condition = normalizeStepCondition(step.condition))
        is com.raycoll.app.domain.model.NextCardStep -> step.copy(condition = normalizeStepCondition(step.condition))
    }
}

private fun normalizeCompoundStep(step: CompoundStep): CompoundStep {
    val params = step.params
    val normalizedParams = when (params) {
        is com.raycoll.app.domain.model.CompoundParams.PresentFront -> params.copy(page = normalizePageIndex(params.page))
        is com.raycoll.app.domain.model.CompoundParams.FlipReveal -> params.copy(revealStyle = if (params.revealStyle == "next") "next" else "all")
        is com.raycoll.app.domain.model.CompoundParams.ShowAllGrade -> params
        is com.raycoll.app.domain.model.CompoundParams.SpeakPauseNext -> params.copy(
            page = normalizePageIndex(params.page),
            nextPage = normalizePageIndex(params.nextPage),
            multiplier = clampMultiplier(params.multiplier)
        )
        is com.raycoll.app.domain.model.CompoundParams.AutoFlip -> params.copy(
            questionPage = normalizePageIndex(params.questionPage),
            answerPage = normalizePageIndex(params.answerPage),
            multiplier = clampMultiplier(params.multiplier)
        )
        is com.raycoll.app.domain.model.CompoundParams.ListenGrade -> params.copy(
            answerPage = normalizePageIndex(params.answerPage),
            threshold = clampThreshold(params.threshold),
            onCorrect = normalizeBranch(params.onCorrect, StepCondition.CORRECT),
            onWrong = normalizeBranch(params.onWrong, StepCondition.WRONG),
        )
        is com.raycoll.app.domain.model.CompoundParams.GradeAfterListen -> params.copy(
            onCorrect = normalizeBranch(params.onCorrect, StepCondition.CORRECT),
            onWrong = normalizeBranch(params.onWrong, StepCondition.WRONG),
        )
        is com.raycoll.app.domain.model.CompoundParams.AutoPassNext -> params.copy(rating = clampRating(params.rating))
        is com.raycoll.app.domain.model.CompoundParams.FailNext -> params
    }
    return step.copy(params = normalizedParams)
}

fun normalizeStudyMode(mode: StudyMode): StudyMode {
    val rawSourceId = if (mode.isBuiltIn && mode.builtInSourceId != null && isBuiltInModeSourceId(mode.builtInSourceId))
        mode.builtInSourceId else null
    val isBuiltIn = rawSourceId != null

    return mode.copy(
        steps = mode.steps.mapNotNull { normalizeModeStep(it) },
        isBuiltIn = isBuiltIn,
        builtInSourceId = rawSourceId,
    )
}

fun normalizeActivityHeatmap(value: Map<String, Int>): Map<String, Int> =
    value.filter { it.value >= 0 }

fun normalizeStoreData(data: StoreData): StoreData = StoreData(
    groups = data.groups.map(::normalizeGroup),
    studyModes = data.studyModes.map(::normalizeStudyMode),
    activityHeatmap = normalizeActivityHeatmap(data.activityHeatmap),
    lastSyncedAt = data.lastSyncedAt,
)
