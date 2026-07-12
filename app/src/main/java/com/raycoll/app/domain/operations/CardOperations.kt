package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.createNewSrsState
import com.raycoll.app.domain.srs.calculateFsrs

fun recordActivity(heatmap: Map<String, Int>, now: Long): Pair<Map<String, Int>, String> {
    val todayKey = LocalDates.dayKey(now)
    val nextHeatmap = heatmap.toMutableMap()
    nextHeatmap[todayKey] = (heatmap[todayKey] ?: 0) + 1
    return Pair(nextHeatmap, todayKey)
}

fun addCard(groups: List<FlashcardGroup>, groupId: String, pages: List<String>, now: Long): Pair<List<FlashcardGroup>, String> {
    val newCardId = Ids.newId()
    val card = Flashcard(id = newCardId, pages = pages, srsState = createNewSrsState(), contentUpdatedAt = now, srsUpdatedAt = 0)
    val nextGroups = groups.map { if (it.id == groupId) it.copy(cards = it.cards + card) else it }
    return Pair(nextGroups, newCardId)
}

fun updateCard(groups: List<FlashcardGroup>, groupId: String, card: Flashcard, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map { if (it.id == card.id) card.copy(contentUpdatedAt = now) else it })
    }

fun deleteCard(groups: List<FlashcardGroup>, groupId: String, cardId: String, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map { if (it.id == cardId) it.copy(deletedAt = now) else it })
    }

fun reviewCard(groups: List<FlashcardGroup>, groupId: String, cardId: String, rating: Int, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map { c ->
            if (c.id == cardId) c.copy(srsState = calculateFsrs(c.srsState, rating, now), srsUpdatedAt = now) else c
        })
    }

fun reviewCardAgain(groups: List<FlashcardGroup>, groupId: String, cardId: String, rating: Int, baseSrsState: SrsState, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map { c ->
            if (c.id == cardId) c.copy(srsState = calculateFsrs(baseSrsState, rating, now), srsUpdatedAt = now) else c
        })
    }
