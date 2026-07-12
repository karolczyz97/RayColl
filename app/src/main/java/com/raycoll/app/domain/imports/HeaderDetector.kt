package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants
import java.text.Normalizer
import kotlin.math.abs

data class HeaderDetectionResult(
    val isLikelyHeader: Boolean,
    val confidence: Double,
)

object HeaderDetector {

    val HEADER_TOKENS = setOf(
        "word", "phrase", "translation", "example", "front", "back", "meaning", "definition",
        "english", "polish", "german", "deutsch", "french", "francais", "italian", "italiano",
        "spanish", "espanol", "palabra", "traduccion", "ejemplo", "wort", "ubersetzung", "beispiel",
        "slowo", "tlumaczenie", "przyklad", "angielski", "polski", "niemiecki", "francuski",
        "wloski", "hiszpanski",
    )

    internal fun normalizeHeaderToken(text: String): String {
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        // NFD nie rozkłada "ł" (litera z kreską, nie znak diakrytyczny)
        return regex.replace(temp, "").replace('ł', 'l').replace('Ł', 'L').lowercase(java.util.Locale.US).trim()
    }

    fun looksLikeHeaderLabel(value: String): Boolean {
        val normalized = normalizeHeaderToken(value)
        if (normalized.isEmpty()) return false
        val words = normalized.split(Regex("\\s+"))
        return words.any { HEADER_TOKENS.contains(it) }
    }

    fun looksLikeShortLabel(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return true
        val words = normalized.split(Regex("\\s+"))
        return normalized.length <= 24 && words.size <= 3
    }

    fun looksLikeDataValue(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return false
        val words = normalized.split(Regex("\\s+"))
        val hasSentenceLikeLength = normalized.length > 24 || words.size >= 5
        val hasDigits = normalized.any { it.isDigit() }
        return hasSentenceLikeLength || hasDigits
    }

    private fun rawRows(text: String, sepKey: String): List<List<String>> =
        parseFullText(normalizeCsvText(text), resolveSep(sepKey))

    fun detectPageCount(text: String, sepKey: String, skipFirstRow: Boolean = false): Int {
        val rows = rawRows(text, sepKey).filter { r -> r.any { it.trim().isNotEmpty() } }
        val dataRows = if (skipFirstRow && rows.size > 1) rows.drop(1) else rows
        if (dataRows.isEmpty()) return 2
        val maxCols = dataRows.maxOf { it.size }
        return maxOf(2, maxCols)
    }

    fun detectFirstRowHeader(
        text: String,
        sepKey: String,
        expectedPageCount: Int? = null,
    ): HeaderDetectionResult {
        val rows = rawRows(text, sepKey)
        if (rows.isEmpty()) return HeaderDetectionResult(false, 0.0)

        val firstRow = rows[0]
        val limit = expectedPageCount ?: firstRow.size
        val firstFields = firstRow.take(limit)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (firstFields.isEmpty()) return HeaderDetectionResult(false, 0.0)

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

        val confidence = (Math.round(score * 100.0) / 100.0).coerceIn(0.0, 1.0)
        return HeaderDetectionResult(
            isLikelyHeader = confidence >= ImportConstants.HEADER_CONFIDENCE_MIN,
            confidence = confidence,
        )
    }
}
