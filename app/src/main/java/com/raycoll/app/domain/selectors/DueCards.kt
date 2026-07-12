package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.*
import kotlin.random.Random

fun filterCards(cards: List<Flashcard>, filter: CardFilter, now: Long): List<Flashcard> {
    val live = cards.filter { it.deletedAt == null }
    return when (filter) {
        CardFilter.all -> live
        CardFilter.new -> live.filter { it.srsState.state == 0 }
        CardFilter.review -> live.filter { it.srsState.state > 0 && it.srsState.nextReviewTimestamp <= now }
        CardFilter.new_review -> live.filter { it.srsState.state == 0 || it.srsState.nextReviewTimestamp <= now }
    }
}

fun shuffleCards(cards: List<Flashcard>): List<Flashcard> {
    val shuffled = cards.toMutableList()
    for (i in shuffled.size - 1 downTo 1) {
        val j = Random.nextInt(i + 1)
        val tmp = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = tmp
    }
    return shuffled
}

fun orderCards(cards: List<Flashcard>, order: CardOrder = CardOrder.sequential): List<Flashcard> =
    when (order) {
        CardOrder.sequential -> cards
        CardOrder.random -> shuffleCards(cards)
        CardOrder.hardest -> cards
            .mapIndexed { index, card -> card to index }
            .sortedWith(compareByDescending<Pair<Flashcard, Int>> { it.first.srsState.difficulty }
                .thenBy { it.first.srsState.nextReviewTimestamp }
                .thenBy { it.second })
            .map { it.first }
    }

fun getDueCards(cards: List<Flashcard>, filter: CardFilter, order: CardOrder = CardOrder.sequential, now: Long): List<Flashcard> =
    orderCards(filterCards(cards, filter, now), order)

fun getGroupProgress(cards: List<Flashcard>): Int {
    val live = cards.filter { it.deletedAt == null }
    if (live.isEmpty()) return 0
    val learned = live.count { it.srsState.repetitions >= 1 }
    return (learned * 100 / live.size)
}
