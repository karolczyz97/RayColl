package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.srs.getCardCategory

data class BrowseFilterResult(
    val all: List<Flashcard>,
    val new: List<Flashcard>,
    val learning: List<Flashcard>,
    val review: List<Flashcard>,
    val mastered: List<Flashcard>,
)

fun categorizeForBrowse(cards: List<Flashcard>): BrowseFilterResult {
    val live = cards.filter { it.deletedAt == null }
    val new = mutableListOf<Flashcard>()
    val learning = mutableListOf<Flashcard>()
    val review = mutableListOf<Flashcard>()
    val mastered = mutableListOf<Flashcard>()
    for (c in live) {
        when (getCardCategory(c.srsState)) {
            com.raycoll.app.domain.srs.SrsCardCategory.New -> new.add(c)
            com.raycoll.app.domain.srs.SrsCardCategory.Learning -> learning.add(c)
            com.raycoll.app.domain.srs.SrsCardCategory.Review -> review.add(c)
            com.raycoll.app.domain.srs.SrsCardCategory.Mastered -> mastered.add(c)
        }
    }
    return BrowseFilterResult(live, new, learning, review, mastered)
}

fun searchCards(cards: List<Flashcard>, query: String, group: FlashcardGroup): List<Flashcard> {
    if (query.isBlank()) return cards.filter { it.deletedAt == null }
    val normalizedQuery = query.lowercase().trim()
    return cards.filter { c ->
        c.deletedAt == null && getVisiblePagesCompat(c, group).any { page ->
            page.lowercase().contains(normalizedQuery)
        }
    }
}

private fun getVisiblePagesCompat(card: Flashcard, group: FlashcardGroup): List<String> {
    val padded = card.pages.toMutableList()
    while (padded.size < group.activePageCount) padded.add("")
    return padded.take(group.activePageCount)
}
