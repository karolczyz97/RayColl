package com.raycoll.app.domain.imports

import com.raycoll.app.domain.normalize.MIN_PAGE_COUNT
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

val HEADER_TOKENS = listOf(
    "word", "phrase", "translation", "example", "front", "back",
    "meaning", "definition", "english", "polish", "german", "deutsch",
    "french", "francais", "italian", "italiano", "spanish", "espanol",
    "palabra", "traduccion", "ejemplo", "wort", "ubersetzung", "beispiel",
    "slowo", "tlumaczenie", "przyklad", "angielski", "polski", "niemiecki",
    "francuski", "wloski", "hiszpanski",
)

private fun normalizeHeaderToken(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .trim()

fun looksLikeHeaderLabel(value: String): Boolean {
    val normalized = normalizeHeaderToken(value)
    if (normalized.isEmpty()) return false
    val words = normalized.split(Regex("\\s+"))
    return words.any { it in HEADER_TOKENS }
}

private fun looksLikeShortLabel(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isEmpty()) return true
    val words = normalized.split(Regex("\\s+"))
    return normalized.length <= 24 && words.size <= 3
}

private fun looksLikeDataValue(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isEmpty()) return false
    val words = normalized.split(Regex("\\s+"))
    return normalized.length > 24 || words.size >= 5 || Regex("\\d").containsMatchIn(normalized)
}

fun detectPageCount(text: String, sepKey: String, skipFirstRow: Boolean = false): Int {
    val rows = parseCSVRaw(text, sepKey).filter { it.any { f -> f.trim().isNotEmpty() } }
    val dataRows = if (skipFirstRow && rows.size > 1) rows.drop(1) else rows
    if (dataRows.isEmpty()) return MIN_PAGE_COUNT
    val maxCols = dataRows.maxOf { it.size }
    return max(MIN_PAGE_COUNT, maxCols)
}

fun detectFirstRowHeader(text: String, sepKey: String, expectedPageCount: Int? = null): Pair<Boolean, Double> {
    val rows = parseCSVRaw(text, sepKey)
    if (rows.isEmpty()) return false to 0.0

    val firstRow = rows[0]
    val firstFields = firstRow
        .take(expectedPageCount ?: firstRow.size)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (firstFields.isEmpty()) return false to 0.0

    val secondRow = rows.getOrElse(1) { emptyList() }
    var score = 0.0
    val keywordMatches = firstFields.count { looksLikeHeaderLabel(it) }
    val shortLabelMatches = firstFields.count { looksLikeShortLabel(it) }
    val dataLikeMatches = firstFields.count { looksLikeDataValue(it) }

    if (keywordMatches > 0) score += if (keywordMatches >= 2) 0.65 else 0.45
    if (shortLabelMatches == firstFields.size) score += 0.15
    if (secondRow.isNotEmpty() && abs(secondRow.size - firstRow.size) <= 1) score += 0.1
    if (dataLikeMatches > 0) score -= minOf(0.35, dataLikeMatches * 0.15)

    val confidence = max(0.0, minOf(1.0, score.coerceIn(0.0, 1.0)))
    return (confidence >= 0.5) to confidence
}

private fun minOf(a: Double, b: Double) = kotlin.math.min(a, b)
