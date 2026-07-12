package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.*
import kotlin.math.min

const val MIN_PAGE_COUNT = 2
const val MAX_VISIBLE_PAGE_COUNT = 5
const val MAX_STORED_PAGE_COUNT = 20
const val MAX_PAUSE_MULTIPLIER = 5
val DEFAULT_STUDY_FILTER: CardFilter = CardFilter.new_review
val DEFAULT_CARD_ORDER: CardOrder = CardOrder.sequential

val VALID_SRS_STATES = setOf(0, 1, 2, 3)
val LANGUAGE_CODE_PATTERN = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$")

fun clampDouble(value: Double, min: Double, max: Double): Double {
    if (value.isNaN()) return min
    return value.coerceIn(min, max)
}

fun clampActivePageCount(candidate: Int, upperBound: Int): Int =
    maxOf(MIN_PAGE_COUNT, minOf(upperBound, candidate))

fun normalizeStudyFilter(filter: CardFilter): CardFilter = filter

fun normalizeCardOrder(order: CardOrder): CardOrder = order

fun padPageMetadata(
    pageNames: List<String>,
    pageLanguages: List<String>,
    count: Int,
): Pair<List<String>, List<String>> {
    val names = pageNames.toMutableList()
    val langs = pageLanguages.toMutableList()
    while (names.size < count) names.add("Page ${names.size + 1}")
    while (langs.size < count) langs.add("en-US")
    return names to langs
}

fun getStoredPageCount(
    group: FlashcardGroup,
    pageNames: List<String>,
    pageLanguages: List<String>,
): Int {
    val maxCardPages = maxOf(0, *group.cards.map { it.pages.size }.toIntArray())
    val rawStored = maxOf(pageNames.size, pageLanguages.size, maxCardPages, MIN_PAGE_COUNT)
    return minOf(rawStored, MAX_STORED_PAGE_COUNT)
}

fun normalizeCard(card: Flashcard): Flashcard {
    val validState = if (card.srsState.state in VALID_SRS_STATES) card.srsState.state else 0
    return card.copy(
        srsState = if (validState != card.srsState.state) card.srsState.copy(state = validState) else card.srsState,
    )
}

fun normalizeGroup(group: FlashcardGroup): FlashcardGroup {
    val rawNames = group.pageNames.filterIsInstance<String>()
    val rawLanguages = group.pageLanguages.filterIsInstance<String>()
    val storedPageCount = getStoredPageCount(group, rawNames, rawLanguages)
    val (pageNames, pageLanguages) = padPageMetadata(rawNames, rawLanguages, storedPageCount)

    val fallback = storedPageCount
    val candidate = group.activePageCount.takeIf { it > 0 } ?: fallback
    val maxVisible = minOf(MAX_VISIBLE_PAGE_COUNT, storedPageCount)
    val activePageCount = clampActivePageCount(candidate, maxVisible)

    return group.copy(
        activePageCount = activePageCount,
        pageNames = pageNames,
        pageLanguages = pageLanguages,
        cards = group.cards.map { normalizeCard(it) },
        studyFilter = normalizeStudyFilter(group.studyFilter),
        cardOrder = normalizeCardOrder(group.cardOrder),
    )
}

fun normalizeModeStep(step: ModeStep): ModeStep? {
    if (step is CompoundStep) return step
    if (step !is AtomicStep) return null

    // Strip invalid conditions
    val condition = when (step.condition) {
        StepCondition.correct -> StepCondition.correct
        StepCondition.wrong -> StepCondition.wrong
        else -> null
    }

    return when (step) {
        is SpeakPage -> step.copy(pageIndex = maxOf(0, step.pageIndex), condition = condition)
        is DynamicPause -> step.copy(
            nextPageIndex = maxOf(0, step.nextPageIndex),
            pauseMultiplier = minOf(MAX_PAUSE_MULTIPLIER, maxOf(0, step.pauseMultiplier)),
            condition = condition
        )
        is AutoRateFixed -> step.copy(
            rating = minOf(4, maxOf(1, step.rating)),
            condition = condition
        )
        is ListenAndCheck -> step.copy(
            pageIndex = maxOf(0, step.pageIndex),
            successThreshold = minOf(100, maxOf(0, step.successThreshold)),
            condition = condition
        )
        is ShowPage -> step.copy(pageIndex = maxOf(0, step.pageIndex), condition = condition)
        is Wait -> step.copy(ms = maxOf(0, step.ms), condition = condition)
        else -> step // steps without condition field keep their raw value
    }
}

fun normalizeStudyMode(mode: StudyMode): StudyMode {
    val rawSourceId = if (mode.isBuiltIn && mode.builtInSourceId != null && isBuiltInModeSourceId(mode.builtInSourceId))
        mode.builtInSourceId else null
    val isBuiltIn = rawSourceId != null

    val steps = if (mode.steps is List<*>) {
        mode.steps.mapNotNull { step ->
            if (step is ModeStep) {
                normalizeModeStep(step)
            } else null
        }
    } else emptyList()

    return mode.copy(
        steps = steps,
        isBuiltIn = isBuiltIn,
        builtInSourceId = rawSourceId,
    )
}

fun normalizeStudyModes(modes: List<StudyMode>): List<StudyMode> = modes.map { normalizeStudyMode(it) }

fun normalizeActivityHeatmap(heatmap: Map<String, Int>): Map<String, Int> =
    heatmap.filterValues { it > 0 }

fun normalizeStoreData(data: StoreData): StoreData = StoreData(
    groups = data.groups.map { normalizeGroup(it) },
    studyModes = normalizeStudyModes(data.studyModes),
    activityHeatmap = normalizeActivityHeatmap(data.activityHeatmap),
    lastSyncedAt = data.lastSyncedAt,
)

// Seed mode source IDs — defined here to avoid circular dep with seed package
private val BUILT_IN_SOURCE_IDS = setOf("classic", "listen-speak")

fun isBuiltInModeSourceId(value: String): Boolean = value in BUILT_IN_SOURCE_IDS
