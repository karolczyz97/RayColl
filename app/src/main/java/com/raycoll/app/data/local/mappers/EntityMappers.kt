package com.raycoll.app.data.local.mappers

import android.util.Log
import com.raycoll.app.data.local.db.ActivityDayEntity
import com.raycoll.app.data.local.db.CardEntity
import com.raycoll.app.data.local.db.DeckEntity
import com.raycoll.app.data.local.db.StudyModeEntity
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val TAG = "EntityMappers"

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
private val stringList = ListSerializer(String.serializer())
private val stepList = ListSerializer(ModeStep.serializer())

/**
 * Kolumny JSON pisze tylko ta aplikacja, ale uszkodzony wiersz (np. po przerwanym
 * zapisie) nie może wyłożyć całego ekranu: logujemy i bierzemy pustą wartość.
 */
private fun <T> decodeOrEmpty(serializer: KSerializer<List<T>>, value: String, what: String): List<T> = try {
    json.decodeFromString(serializer, value)
} catch (e: Exception) {
    Log.w(TAG, "Corrupt $what column, using empty list", e)
    emptyList()
}

/** Kolumna JSON z listą tekstów (strony karty, nazwy/języki stron) — także do punktowych UPDATE. */
fun encodeStringList(values: List<String>): String = json.encodeToString(stringList, values)

fun FlashcardGroup.toEntity(namespace: String) = DeckEntity(
    id = id,
    namespace = namespace,
    name = name,
    activeModeId = activeModeId,
    studyFilter = studyFilter.key,
    cardOrder = cardOrder.key,
    pageNamesJson = encodeStringList(pageNames),
    pageLanguagesJson = encodeStringList(pageLanguages),
    activePageCount = activePageCount,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    archivedAt = archivedAt
)

fun DeckEntity.toDomain(cards: List<CardEntity>) = FlashcardGroup(
    id = id,
    name = name,
    cards = cards.map { it.toDomain() },
    activeModeId = activeModeId,
    studyFilter = StudyFilter.fromKey(studyFilter),
    cardOrder = CardOrder.fromKey(cardOrder),
    pageNames = decodeOrEmpty(stringList, pageNamesJson, "deck.pageNames"),
    pageLanguages = decodeOrEmpty(stringList, pageLanguagesJson, "deck.pageLanguages"),
    activePageCount = activePageCount,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    archivedAt = archivedAt
)

/** Talie z ich kartami (karty rozdzielone po `deckId`); kolejność talii i kart zostaje. */
fun List<DeckEntity>.toDomain(cards: List<CardEntity>): List<FlashcardGroup> {
    val cardsByDeck = cards.groupBy { it.deckId }
    return map { it.toDomain(cardsByDeck[it.id].orEmpty()) }
}

fun Flashcard.toEntity(namespace: String, deckId: String) = CardEntity(
    id = id,
    namespace = namespace,
    deckId = deckId,
    pagesJson = encodeStringList(pages),
    difficulty = srsState.difficulty,
    stability = srsState.stability,
    repetitions = srsState.repetitions,
    state = srsState.state,
    lastReviewTimestamp = srsState.lastReviewTimestamp,
    nextReviewTimestamp = srsState.nextReviewTimestamp,
    contentUpdatedAt = contentUpdatedAt,
    srsUpdatedAt = srsUpdatedAt,
    deletedAt = deletedAt
)

fun CardEntity.toDomain() = Flashcard(
    id = id,
    pages = decodeOrEmpty(stringList, pagesJson, "card.pages"),
    srsState = SrsState(
        difficulty = difficulty,
        stability = stability,
        repetitions = repetitions,
        state = state,
        lastReviewTimestamp = lastReviewTimestamp,
        nextReviewTimestamp = nextReviewTimestamp
    ),
    contentUpdatedAt = contentUpdatedAt,
    srsUpdatedAt = srsUpdatedAt,
    deletedAt = deletedAt
)

fun StudyMode.toEntity(namespace: String) = StudyModeEntity(
    id = id,
    namespace = namespace,
    name = name,
    stepsJson = json.encodeToString(stepList, steps),
    isBuiltIn = isBuiltIn,
    builtInSourceId = builtInSourceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun StudyModeEntity.toDomain() = StudyMode(
    id = id,
    name = name,
    steps = decodeOrEmpty(stepList, stepsJson, "mode.steps"),
    isBuiltIn = isBuiltIn,
    builtInSourceId = builtInSourceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun List<ActivityDayEntity>.toHeatmap(): Map<String, Int> = associate { it.dayKey to it.count }

fun Map<String, Int>.toActivityEntities(namespace: String): List<ActivityDayEntity> =
    map { (dayKey, count) -> ActivityDayEntity(dayKey = dayKey, namespace = namespace, count = count) }
