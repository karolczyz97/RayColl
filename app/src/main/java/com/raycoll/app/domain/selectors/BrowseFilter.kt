package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.domain.srs.getCardCategory

/** Karty przeglądarki: kategoria SRS + wyszukiwanie w aktywnych stronach (bez wielkości liter). */
fun browseFilter(
    cards: List<Flashcard>,
    search: String,
    category: SrsCardCategory?,
    activePageCount: Int
): List<Flashcard> {
    val query = search.trim()
    return cards.live().filter { card ->
        (category == null || getCardCategory(card.srsState) == category) &&
            (query.isEmpty() || card.pages.take(activePageCount).any { it.contains(query, ignoreCase = true) })
    }
}
