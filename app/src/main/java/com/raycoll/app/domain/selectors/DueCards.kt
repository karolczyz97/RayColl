package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.StudyFilter
import kotlin.random.Random

/** Snapshoty kopii/chmury zawierają tombstony, nawet gdy zapytania ekranów już je odfiltrowały. */
fun List<Flashcard>.live(): List<Flashcard> = filter { it.deletedAt == null }

fun Flashcard.matches(filter: StudyFilter, now: Long): Boolean {
    val isNew = srsState.state == 0
    val isDue = srsState.nextReviewTimestamp <= now
    return when (filter) {
        StudyFilter.ALL -> true
        StudyFilter.NEW -> isNew
        StudyFilter.REVIEW -> !isNew && isDue
        StudyFilter.NEW_AND_REVIEW -> isNew || isDue
    }
}

fun orderCards(cards: List<Flashcard>, order: CardOrder, random: Random = Random): List<Flashcard> = when (order) {
    CardOrder.SEQUENTIAL -> cards

    CardOrder.RANDOM -> cards.shuffled(random)

    // stabilnie: przy równej trudności i terminie zostaje kolejność z talii
    CardOrder.HARDEST -> cards.sortedWith(
        compareByDescending<Flashcard> { it.srsState.difficulty }.thenBy { it.srsState.nextReviewTimestamp }
    )
}

fun getDueCards(
    cards: List<Flashcard>,
    filter: StudyFilter,
    order: CardOrder,
    now: Long,
    random: Random = Random
): List<Flashcard> = orderCards(cards.live().filter { it.matches(filter, now) }, order, random)

fun countDueCards(cards: List<Flashcard>, filter: StudyFilter, now: Long): Int =
    cards.count { it.deletedAt == null && it.matches(filter, now) }
