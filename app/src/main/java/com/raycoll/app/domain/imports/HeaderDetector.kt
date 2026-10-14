package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants
import com.raycoll.app.domain.model.PageLimits
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object HeaderDetector {

    val HEADER_TOKENS = setOf(
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

    fun looksLikeHeaderLabel(value: String): Boolean {
        val normalized = normalizeHeaderToken(value)
        if (normalized.isEmpty()) return false
        val words = normalized.split(WHITESPACE)
        return words.any { HEADER_TOKENS.contains(it) }
    }

    fun looksLikeShortLabel(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return true
        val words = normalized.split(WHITESPACE)
        return normalized.length <= 24 && words.size <= 3
    }

    fun looksLikeDataValue(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return false
        val words = normalized.split(WHITESPACE)
        val hasSentenceLikeLength = normalized.length > 24 || words.size >= 5
        val hasDigits = normalized.any { it.isDigit() }
        return hasSentenceLikeLength || hasDigits
    }

    /** Liczba stron = najszersza linia danych (co najmniej minimalna liczba stron). */
    fun detectPageCount(rows: List<List<String>>, skipFirstRow: Boolean = false): Int {
        val dataRows = if (skipFirstRow && rows.size > 1) rows.drop(1) else rows
        return maxOf(PageLimits.MIN_PAGE_COUNT, dataRows.maxOfOrNull { it.size } ?: 0)
    }

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
            score += if (keywordMatches >= 2) 0.65 else 0.45
        }
        if (shortLabelMatches == firstFields.size) {
            score += 0.15
        }
        if (secondRow.isNotEmpty() && abs(secondRow.size - firstRow.size) <= 1) {
            score += 0.1
        }
        if (dataLikeMatches > 0) {
            score -= minOf(0.35, dataLikeMatches.toDouble() * 0.15)
        }

        val confidence = ((score * 100).roundToInt() / 100.0).coerceIn(0.0, 1.0)
        return confidence >= ImportConstants.HEADER_CONFIDENCE_MIN
    }
}
