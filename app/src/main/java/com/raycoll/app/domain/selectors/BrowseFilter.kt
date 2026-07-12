package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.domain.srs.getCardCategory

data class BrowseFilterResult(val cards: List<Flashcard>, val counts: CardStats)

fun browseFilter(cards: List<Flashcard>, search: String, category: SrsCardCategory?, now: Long): BrowseFilterResult {
    val liveCards = filterLiveCards(cards)
    val searchLower = search.trim().lowercase()
    val filtered = if (searchLower.isEmpty()) {
        if (category == null) liveCards else liveCards.filter { getCardCategory(it.srsState) == category }
    } else {
        liveCards.filter { card ->
            (category == null || getCardCategory(card.srsState) == category) &&
            card.pages.any { it.lowercase().contains(searchLower) }
        }
    }
    return BrowseFilterResult(cards = filtered, counts = computeCardStats(liveCards))
}
