package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.ImportConstants
import com.raycoll.app.domain.model.PageLimits

class ImportPayload(
    val deckName: String,
    val pageNames: List<String>,
    val pageLanguages: List<String>,
    val cards: List<List<String>>
)

/** Dlaczego importu nie da się wykonać — UI zamienia to na komunikat. */
sealed interface ImportProblem {
    data object DeckNameRequired : ImportProblem
    data object InvalidPageCount : ImportProblem
    data class InvalidLanguage(val code: String) : ImportProblem
    data object NoCards : ImportProblem
    data class EmptyRow(val rowNumber: Int) : ImportProblem
}

/** Pierwszy problem z danymi importu albo null, gdy można importować. */
fun validateImportPayload(payload: ImportPayload): ImportProblem? {
    val pageCount = payload.pageNames.size
    return when {
        payload.deckName.isBlank() -> ImportProblem.DeckNameRequired

        pageCount !in PageLimits.MIN_PAGE_COUNT..PageLimits.MAX_STORED_PAGE_COUNT ||
            payload.pageLanguages.size != pageCount ->
            ImportProblem.InvalidPageCount

        else -> payload.pageLanguages.firstOrNull {
            !ImportConstants.LANG_CODE_REGEX.matches(it)
        }?.let(ImportProblem::InvalidLanguage)
            ?: if (payload.cards.isEmpty()) {
                ImportProblem.NoCards
            } else {
                payload.cards.indexOfFirst { row -> row.take(pageCount).all { it.isBlank() } }
                    .takeIf { it >= 0 }
                    ?.let { ImportProblem.EmptyRow(it + 1) }
            }
    }
}
