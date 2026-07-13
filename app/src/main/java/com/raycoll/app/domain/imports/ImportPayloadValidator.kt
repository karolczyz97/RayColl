package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants
import com.raycoll.app.domain.model.PageLimits

// Detekcja separatora/nagłówka/liczby stron mieszka w HeaderDetector.kt (port 1:1
// z headerDetection.ts). Ten plik trzyma tylko wykrywanie języka i walidację payloadu.

object LanguageDetector {
    // tokeny porównywane PO normalizacji (lowercase, bez diakrytyków), per słowo nagłówka
    private data class LangTokens(val tag: String, val exact: Set<String>, val prefixes: Set<String>)

    private val LANGS = listOf(
        LangTokens("en-US", setOf("word", "phrase", "english", "translation", "example", "front", "back", "meaning", "definition", "en", "en-us", "en-gb"), setOf("angiel")),
        LangTokens("pl-PL", setOf("slowo", "polish", "polski", "pl", "pl-pl", "tlumaczenie", "przyklad"), setOf("polsk", "fraz")),
        LangTokens("es-ES", setOf("palabra", "es", "es-es", "spanish", "espanol", "traduccion", "ejemplo"), setOf("hiszpan")),
        LangTokens("de-DE", setOf("wort", "deutsch", "de", "de-de", "ubersetzung", "beispiel"), setOf("niemiec")),
        LangTokens("fr-FR", setOf("french", "francais", "fr", "fr-fr"), setOf("franc")),
        LangTokens("it-IT", setOf("italiano", "italian", "it", "it-it"), setOf("wlos")),
    )

    fun detectLanguage(cell: String): String {
        val normalized = HeaderDetector.normalizeHeaderToken(cell)
        if (normalized.isEmpty()) return "en-US"
        val words = normalized.split(Regex("\\s+"))
        for (lang in LANGS) {
            if (words.any { w -> lang.exact.contains(w) || lang.prefixes.any { p -> w.startsWith(p) } }) return lang.tag
        }
        return "en-US"
    }

    /** Alias zgodny z nazwą ze starej apki (languageDetection.ts). */
    fun detectLangFromHeader(header: String): String = detectLanguage(header)
}

class ImportPayload(
    val deckName: String,
    val pageNames: List<String>,
    val pageLanguages: List<String>,
    val cards: List<List<String>>,
)

class ImportValidationException(message: String) : Exception(message)

fun validateImportPayload(payload: ImportPayload) {
    if (payload.deckName.isBlank()) throw ImportValidationException("Deck name is required")
    val pageCount = payload.pageNames.size
    if (pageCount < PageLimits.MIN_PAGE_COUNT || pageCount > PageLimits.MAX_STORED_PAGE_COUNT)
        throw ImportValidationException("Page count must be between ${PageLimits.MIN_PAGE_COUNT} and ${PageLimits.MAX_STORED_PAGE_COUNT}")
    if (payload.pageLanguages.size != pageCount)
        throw ImportValidationException("Languages count must match page count")
    for (lang in payload.pageLanguages) {
        if (!ImportConstants.LANG_CODE_REGEX.matches(lang))
            throw ImportValidationException("Invalid language code: $lang")
    }
    if (payload.cards.isEmpty()) throw ImportValidationException("At least one card is required")
    payload.cards.forEachIndexed { index, row ->
        if (row.size < pageCount) throw ImportValidationException("Row ${index + 1} is missing fields")
        if (row.take(pageCount).all { it.isBlank() }) throw ImportValidationException("Row ${index + 1} is empty")
    }
}
