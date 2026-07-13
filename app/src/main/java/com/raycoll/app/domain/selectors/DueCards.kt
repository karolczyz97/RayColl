package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.normalize.normalizeCardOrder
import kotlin.math.roundToInt
import kotlin.random.Random

private data class IndexedCard(val card: Flashcard, val index: Int)

fun filterLiveCards(cards: List<Flashcard>): List<Flashcard> = cards.filter { it.deletedAt == null }

fun filterCards(cards: List<Flashcard>, filter: String, now: Long): List<Flashcard> {
    val liveCards = filterLiveCards(cards)
    return when (filter) {
        "all" -> liveCards.toList()
        "new" -> liveCards.filter { it.srsState.state == 0 }
        "review" -> liveCards.filter { it.srsState.state > 0 && it.srsState.nextReviewTimestamp <= now }
        "new+review" -> liveCards.filter { it.srsState.state == 0 || it.srsState.nextReviewTimestamp <= now }
        else -> liveCards.toList()
    }
}

fun orderCards(cards: List<Flashcard>, order: String?, random: Random = Random): List<Flashcard> {
    val normalized = normalizeCardOrder(order)
    return when (normalized) {
        "random" -> {
            // Fisher-Yates: i biegnie od ostatniego indeksu do 1 (parytet z csvParser.ts
            // shuffleCards). Wcześniejszy `indices.reversed().drop(1)` gubił i=n-1, przez
            // co ostatnia karta nigdy nie była tasowana i zawsze zostawała na końcu.
            val shuffled = cards.toMutableList()
            for (i in shuffled.size - 1 downTo 1) {
                val target = random.nextInt(i + 1)
                val tmp = shuffled[i]; shuffled[i] = shuffled[target]; shuffled[target] = tmp
            }
            shuffled
        }
        "hardest" -> {
            val indexed = cards.mapIndexed { index, card -> IndexedCard(card, index) }
            indexed.sortedWith(
                compareByDescending<IndexedCard> { it.card.srsState.difficulty }
                    .thenBy { it.card.srsState.nextReviewTimestamp }
                    .thenBy { it.index }
            ).map { it.card }
        }
        else -> cards.toList()
    }
}

fun getDueCards(cards: List<Flashcard>, filter: String, order: String?, now: Long, random: Random = Random): List<Flashcard> =
    orderCards(filterCards(cards, filter, now), order, random)

fun getGroupProgress(cards: List<Flashcard>): Int {
    val liveCards = filterLiveCards(cards)
    if (liveCards.isEmpty()) return 0
    val learned = liveCards.count { it.srsState.repetitions >= 1 }
    // round (nie trunc) — parytet z Math.round w cardSelectors.ts i spec 6.2.
    return ((learned.toDouble() / liveCards.size) * 100).roundToInt()
}
