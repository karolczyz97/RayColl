package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants
import com.raycoll.app.domain.model.PageLimits
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object HeaderDetector {

    private const val LABEL_MAX_CHARACTERS = 24
    private const val LABEL_MAX_WORDS = 3
    private const val DATA_MIN_WORDS = 5
    private const val MULTIPLE_KEYWORDS_WEIGHT = 0.65
    private const val SINGLE_KEYWORD_WEIGHT = 0.45
    private const val SHORT_LABELS_WEIGHT = 0.15
    private const val SIMILAR_WIDTH_WEIGHT = 0.1
    private const val DATA_PENALTY_MAX = 0.35
    private const val DATA_FIELD_PENALTY = 0.15

    private val HEADER_TOKENS = setOf(
        "word", "phrase", "translation", "example", "front", "back", "meaning", "definition",
        "english", "polish", "german", "deutsch", "french", "francais", "italian", "italiano",
        "spanish", "espanol", "palabra", "traduccion", "ejemplo", "wort", "ubersetzung", "beispiel",
        "slowo", "tlumaczenie", "przyklad", "angielski", "polski", "niemiecki", "francuski",
        "wloski", "hiszpanski"
    )

    private val COMBINING_MARKS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    internal val WHITESPACE = Regex("\\s+")

    internal fun normalizeHeaderToken(text: String): String {
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        // NFD nie rozkłada "ł" (litera z kreską, nie znak diakrytyczny)
        return COMBINING_MARKS.replace(temp, "").replace('ł', 'l').replace('Ł', 'L').lowercase(Locale.ROOT).trim()
    }

    private fun looksLikeHeaderLabel(value: String): Boolean {
        val normalized = normalizeHeaderToken(value)
        if (normalized.isEmpty()) return false
        val words = normalized.split(WHITESPACE)
        return words.any { HEADER_TOKENS.contains(it) }
    }

    private fun looksLikeShortLabel(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return true
        val words = normalized.split(WHITESPACE)
        return normalized.length <= LABEL_MAX_CHARACTERS && words.size <= LABEL_MAX_WORDS
    }

    private fun looksLikeDataValue(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return false
        val words = normalized.split(WHITESPACE)
        val hasSentenceLikeLength = normalized.length > LABEL_MAX_CHARACTERS || words.size >= DATA_MIN_WORDS
        val hasDigits = normalized.any { it.isDigit() }
        return hasSentenceLikeLength || hasDigits
    }

    /** Puste końcowe kolumny bez nazwy są pomijane; puste wewnętrzne i nazwane pozostają. */
    fun detectPageCount(rows: List<List<String>>, skipFirstRow: Boolean = false): Int {
        val dataRows = if (skipFirstRow) rows.drop(1) else rows
        val headerWidth = if (skipFirstRow) rows.firstOrNull()?.meaningfulWidth() ?: 0 else 0
        return maxOf(PageLimits.MIN_PAGE_COUNT, headerWidth, dataRows.maxOfOrNull { it.meaningfulWidth() } ?: 0)
    }

    private fun List<String>.meaningfulWidth(): Int = indexOfLast { it.isNotBlank() } + 1

    fun looksLikeHeaderRow(rows: List<List<String>>): Boolean {
        if (rows.isEmpty()) return false

        val firstRow = rows[0]
        val firstFields = firstRow
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (firstFields.isEmpty()) return false

        val secondRow = rows.getOrNull(1) ?: emptyList()
        var score = 0.0
        val keywordMatches = firstFields.count { looksLikeHeaderLabel(it) }
        val shortLabelMatches = firstFields.count { looksLikeShortLabel(it) }
        val dataLikeMatches = firstFields.count { looksLikeDataValue(it) }

        if (keywordMatches > 0) {
            score += if (keywordMatches >= 2) MULTIPLE_KEYWORDS_WEIGHT else SINGLE_KEYWORD_WEIGHT
        }
        if (shortLabelMatches == firstFields.size) {
            score += SHORT_LABELS_WEIGHT
        }
        if (secondRow.isNotEmpty() && abs(secondRow.size - firstRow.size) <= 1) {
            score += SIMILAR_WIDTH_WEIGHT
        }
        if (dataLikeMatches > 0) {
            score -= minOf(DATA_PENALTY_MAX, dataLikeMatches.toDouble() * DATA_FIELD_PENALTY)
        }

        val confidence = ((score * 100).roundToInt() / 100.0).coerceIn(0.0, 1.0)
        return confidence >= ImportConstants.HEADER_CONFIDENCE_MIN
    }
}
