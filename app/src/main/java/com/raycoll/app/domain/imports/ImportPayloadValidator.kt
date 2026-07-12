package com.raycoll.app.domain.imports

import com.raycoll.app.domain.normalize.LANGUAGE_CODE_PATTERN
import com.raycoll.app.domain.normalize.MIN_PAGE_COUNT
import com.raycoll.app.domain.normalize.MAX_STORED_PAGE_COUNT

data class ImportDeckPayload(
    val name: String,
    val languages: List<String>,
    val pageNames: List<String>,
    val cards: List<List<String>>,
)

data class NormalizedImportDeckPayload(
    val name: String,
    val languages: List<String>,
    val pageNames: List<String>,
    val cards: List<List<String>>,
)

fun validateImportDeckPayload(payload: ImportDeckPayload): NormalizedImportDeckPayload {
    val name = payload.name.trim()
    require(name.isNotEmpty()) { "Deck name is required." }

    val pageCount = payload.pageNames.size
    require(pageCount >= MIN_PAGE_COUNT) { "Decks must contain at least $MIN_PAGE_COUNT pages." }
    require(pageCount <= MAX_STORED_PAGE_COUNT) { "Decks can contain at most $MAX_STORED_PAGE_COUNT pages." }

    val languages = payload.languages.take(pageCount)
    require(languages.size == pageCount) { "Every page must have a language entry." }
    languages.forEachIndexed { i, lang ->
        require(LANGUAGE_CODE_PATTERN.matches(lang)) { "Missing language for page ${i + 1}" }
    }

    require(payload.cards.isNotEmpty()) { "Import does not contain any cards." }

    val cards = payload.cards.mapIndexed { index, card ->
        require(card.isNotEmpty()) { "Row ${index + 1} is empty." }
        require(card.size >= pageCount) { "Row ${index + 1} is missing one or more pages." }
        card.take(pageCount).also { require(it.any { p -> p.trim().isNotEmpty() }) { "Row ${index + 1} is empty." } }
    }

    return NormalizedImportDeckPayload(name, languages, payload.pageNames, cards)
}
