package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.srs.calculateFsrs
import com.raycoll.app.domain.srs.createNewSrsState
import java.util.UUID

fun recordActivity(heatmap: Map<String, Int>, todayKey: String): Pair<Map<String, Int>, String> {
    val next = heatmap.toMutableMap()
    next[todayKey] = (next[todayKey] ?: 0) + 1
    return next to todayKey
}

fun addFlashcard(groups: List<FlashcardGroup>, groupId: String, pages: List<String>, now: Long): Pair<List<FlashcardGroup>, String> {
    val newCardId = UUID.randomUUID().toString()
    val card = Flashcard(id = newCardId, pages = pages, srsState = createNewSrsState(), contentUpdatedAt = now)
    val next = groups.map { if (it.id == groupId) it.copy(cards = it.cards + card) else it }
    return next to newCardId
}

fun updateFlashcard(groups: List<FlashcardGroup>, groupId: String, card: Flashcard, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map { if (it.id == card.id) card.copy(contentUpdatedAt = now) else it })
    }

fun deleteFlashcard(groups: List<FlashcardGroup>, groupId: String, cardId: String, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map { if (it.id == cardId) it.copy(deletedAt = now) else it })
    }

private fun reviewFlashcardInternal(groups: List<FlashcardGroup>, groupId: String, cardId: String, rating: Int, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map {
            if (it.id == cardId) it.copy(srsState = calculateFsrs(it.srsState, rating, now), srsUpdatedAt = now)
            else it
        })
    }

fun reviewFlashcard(groups: List<FlashcardGroup>, groupId: String, cardId: String, rating: Int, heatmap: Map<String, Int>, todayKey: String, now: Long): Triple<List<FlashcardGroup>, Map<String, Int>, String> {
    val next = reviewFlashcardInternal(groups, groupId, cardId, rating, now)
    val (nextHeatmap, tk) = recordActivity(heatmap, todayKey)
    return Triple(next, nextHeatmap, tk)
}

fun reviewFlashcardAgain(groups: List<FlashcardGroup>, groupId: String, cardId: String, rating: Int, baseSrsState: SrsState, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards.map {
            if (it.id == cardId) it.copy(srsState = calculateFsrs(baseSrsState, rating, now), srsUpdatedAt = now)
            else it
        })
    }

fun addFlashcardsBulk(groups: List<FlashcardGroup>, groupId: String, cards: List<Flashcard>): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else g.copy(cards = g.cards + cards)
    }
