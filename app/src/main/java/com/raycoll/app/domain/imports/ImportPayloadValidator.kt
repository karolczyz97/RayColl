package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants
import com.raycoll.app.domain.model.PageLimits

fun detectPageCount(rows: List<List<String>>, skipFirstRow: Boolean): Int {
    val dataRows = if (skipFirstRow && rows.isNotEmpty()) rows.drop(1) else rows
    val max = dataRows.maxOfOrNull { it.size } ?: 0
    return maxOf(max, PageLimits.MIN_PAGE_COUNT)
}

fun detectFirstRowHeader(rows: List<List<String>>): Boolean {
    if (rows.isEmpty()) return false
    val first = rows[0]
    if (first.all { it.isBlank() }) return false

    val headerKeywords = setOf("word", "phrase", "translation", "tłumaczenie", "słowo", "palabra",
        "wort", "parola", "english", "polish", "german", "spanish", "italian", "front", "back",
        "przód", "tył", "question", "answer", "pytanie", "odpowiedź")

    var score = 0.0
    val matchingFields = first.count { cell ->
        val normalized = cell.lowercase().trim()
        headerKeywords.any { keyword -> normalized.contains(keyword) }
    }
    score += if (matchingFields >= 2) 0.65 else if (matchingFields == 1) 0.45 else 0.0
    val shortLabels = first.count { cell -> cell.length <= 24 && cell.split(" ").size <= 3 }
    if (shortLabels == first.size) score += 0.15
    if (rows.size > 1) {
        val secondCols = rows[1].size
        if (kotlin.math.abs(secondCols - first.size) <= 1) score += 0.1
    }
    val dataLike = first.count { cell -> cell.length > 50 || cell.split(" ").size >= 5 || cell.any { it.isDigit() } }
    score -= minOf(dataLike * 0.15, 0.35)
    return score >= ImportConstants.HEADER_CONFIDENCE_MIN
}

object LanguageDetector {
    private val exactTokens = mapOf(
        "en" to listOf("english", "angielski", "en", "en-us", "en-gb"),
        "pl" to listOf("polish", "polski", "pl", "pl-pl"),
        "es" to listOf("spanish", "hiszpański", "es", "es-es"),
        "de" to listOf("german", "niemiecki", "de", "de-de"),
        "fr" to listOf("french", "francuski", "fr", "fr-fr"),
        "it" to listOf("italian", "włoski", "it", "it-it"),
    )

    fun detectLanguage(cell: String): String {
        val normalized = cell.lowercase().trim()
        for ((code, tokens) in exactTokens) {
            if (tokens.any { it == normalized }) return "$code-${code.uppercase()}"
        }
        for ((code, tokens) in exactTokens) {
            if (tokens.any { normalized.startsWith(it) }) return "$code-${code.uppercase()}"
        }
        return "en-US"
    }
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
