package com.raycoll.app.data.local.mappers

import com.raycoll.app.data.local.db.CardEntity
import com.raycoll.app.data.local.db.DeckEntity
import com.raycoll.app.data.local.db.StudyModeEntity
import com.raycoll.app.data.local.db.ActivityDayEntity
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.StoreData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Mappers {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun deckToEntity(deck: FlashcardGroup, namespace: String): DeckEntity = DeckEntity(
        id = deck.id, namespace = namespace, name = deck.name,
        activeModeId = deck.activeModeId, studyFilter = deck.studyFilter,
        cardOrder = deck.cardOrder,
        pageNamesJson = json.encodeToString(deck.pageNames),
        pageLanguagesJson = json.encodeToString(deck.pageLanguages),
        activePageCount = deck.activePageCount,
        updatedAt = deck.updatedAt, deletedAt = deck.deletedAt, archivedAt = deck.archivedAt,
    )

    fun entityToDeck(entity: DeckEntity, cards: List<CardEntity>): FlashcardGroup = FlashcardGroup(
        id = entity.id, name = entity.name, cards = cards.map(::entityToCard),
        activeModeId = entity.activeModeId, studyFilter = entity.studyFilter,
        cardOrder = entity.cardOrder,
        pageNames = json.decodeFromString(entity.pageNamesJson),
        pageLanguages = json.decodeFromString(entity.pageLanguagesJson),
        activePageCount = entity.activePageCount,
        updatedAt = entity.updatedAt, deletedAt = entity.deletedAt, archivedAt = entity.archivedAt,
    )

    fun cardToEntity(card: Flashcard, namespace: String, deckId: String): CardEntity = CardEntity(
        id = card.id, namespace = namespace, deckId = deckId,
        pagesJson = json.encodeToString(card.pages),
        difficulty = card.srsState.difficulty, stability = card.srsState.stability,
        repetitions = card.srsState.repetitions, state = card.srsState.state,
        lastReviewTimestamp = card.srsState.lastReviewTimestamp,
        nextReviewTimestamp = card.srsState.nextReviewTimestamp,
        contentUpdatedAt = card.contentUpdatedAt, srsUpdatedAt = card.srsUpdatedAt,
        deletedAt = card.deletedAt,
    )

    fun entityToCard(entity: CardEntity): Flashcard = Flashcard(
        id = entity.id,
        pages = json.decodeFromString(entity.pagesJson),
        srsState = SrsState(
            difficulty = entity.difficulty, stability = entity.stability,
            repetitions = entity.repetitions, state = entity.state,
            lastReviewTimestamp = entity.lastReviewTimestamp,
            nextReviewTimestamp = entity.nextReviewTimestamp,
        ),
        contentUpdatedAt = entity.contentUpdatedAt, srsUpdatedAt = entity.srsUpdatedAt,
        deletedAt = entity.deletedAt,
    )

    fun modeToEntity(mode: StudyMode, namespace: String): StudyModeEntity = StudyModeEntity(
        id = mode.id, namespace = namespace, name = mode.name,
        stepsJson = json.encodeToString(mode.steps),
        isBuiltIn = mode.isBuiltIn, builtInSourceId = mode.builtInSourceId,
        updatedAt = mode.updatedAt, deletedAt = mode.deletedAt,
    )

    fun entityToMode(entity: StudyModeEntity): StudyMode {
        val steps: List<ModeStep> = try { json.decodeFromString(entity.stepsJson) } catch (_: Exception) { emptyList() }
        return StudyMode(
            id = entity.id, name = entity.name, steps = steps,
            isBuiltIn = entity.isBuiltIn, builtInSourceId = entity.builtInSourceId,
            updatedAt = entity.updatedAt, deletedAt = entity.deletedAt,
        )
    }

    fun activityToEntity(dayKey: String, count: Int, namespace: String): ActivityDayEntity =
        ActivityDayEntity(dayKey = dayKey, namespace = namespace, count = count)

    fun entityToActivity(entity: ActivityDayEntity): Pair<String, Int> =
        entity.dayKey to entity.count
}
