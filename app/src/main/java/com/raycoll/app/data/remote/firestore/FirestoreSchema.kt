package com.raycoll.app.data.remote.firestore

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.StoreData

object FirestoreSchema {
    const val PATH_USERS = "users"
    const val PATH_DECKS = "decks"
    const val PATH_CARDS = "cards"
    const val PATH_MODES = "studyModes"
    const val PATH_ACTIVITY = "activity"

    fun deckToMap(deck: FlashcardGroup): Map<String, Any?> = mapOf(
        "id" to deck.id,
        "name" to deck.name,
        "activeModeId" to deck.activeModeId,
        "studyFilter" to deck.studyFilter,
        "cardOrder" to deck.cardOrder,
        "pageNames" to deck.pageNames,
        "pageLanguages" to deck.pageLanguages,
        "activePageCount" to deck.activePageCount,
        "updatedAt" to deck.updatedAt,
        "deletedAt" to deck.deletedAt,
        "archivedAt" to deck.archivedAt,
    )

    fun cardToMap(card: Flashcard): Map<String, Any?> = mapOf(
        "id" to card.id,
        "pages" to card.pages,
        "difficulty" to card.srsState.difficulty,
        "stability" to card.srsState.stability,
        "repetitions" to card.srsState.repetitions,
        "state" to card.srsState.state,
        "lastReviewTimestamp" to card.srsState.lastReviewTimestamp,
        "nextReviewTimestamp" to card.srsState.nextReviewTimestamp,
        "contentUpdatedAt" to card.contentUpdatedAt,
        "srsUpdatedAt" to card.srsUpdatedAt,
        "deletedAt" to card.deletedAt,
    )

    fun modeToMap(mode: StudyMode): Map<String, Any?> = mapOf(
        "id" to mode.id,
        "name" to mode.name,
        "stepsJson" to mode.steps,
        "isBuiltIn" to mode.isBuiltIn,
        "builtInSourceId" to mode.builtInSourceId,
        "updatedAt" to mode.updatedAt,
        "deletedAt" to mode.deletedAt,
    )

    fun activityToMap(dayKey: String, count: Int): Map<String, Any?> = mapOf(
        "count" to count,
    )
}
