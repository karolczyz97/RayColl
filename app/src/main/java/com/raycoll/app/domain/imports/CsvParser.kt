package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants
import org.apache.commons.csv.CSVFormat

/** Separator kolumn w imporcie tekstowym. */
enum class CsvSeparator(val char: Char) {
    TAB('\t'),
    SEMICOLON(';'),
    COMMA(','),
    PIPE('|')
    ;

    companion object {
        val DEFAULT = SEMICOLON
    }
}

private val LINE_BREAKS = Regex("\r\n?")

/** Ujednolica tekst: bez znacznika BOM, końce linii jako "\n". */
private fun normalizeCsvText(text: String): String = text.removePrefix("\uFEFF").replace(LINE_BREAKS, "\n")

private fun csvFormat(separator: CsvSeparator): CSVFormat = CSVFormat.DEFAULT.builder()
    .setDelimiter(separator.char)
    .setIgnoreSurroundingSpaces(true)
    .setIgnoreEmptyLines(true)
    .setTrailingData(true)
    .setLenientEof(true)
    .get()

/**
 * Dzieli tekst na wiersze i komórki (cudzysłowy jak w CSV: `"a;b"`, `""` = cudzysłów).
 * Komórki są przycięte, puste wiersze pominięte; parsowanie kończy się po [maxRows] wierszach.
 */
fun parseRows(text: String, separator: CsvSeparator, maxRows: Int = Int.MAX_VALUE): List<List<String>> {
    if (maxRows <= 0) return emptyList()
    return csvFormat(separator).parse(normalizeCsvText(text).reader()).use { parser ->
        parser.asSequence()
            .map { row -> row.map { it.trim() } }
            .filter { row -> row.any { it.isNotBlank() } }
            .take(maxRows)
            .toList()
    }
}

private data class SeparatorCandidate(val separator: CsvSeparator, val consistency: Double, val width: Int)

private const val CONSISTENT_TAB_SHARE = 0.8

/** Spójny TSV z arkusza ma pierwszeństwo; pozostałe formaty porównujemy po stałej szerokości. */
fun detectSeparator(text: String): CsvSeparator {
    val candidates = CsvSeparator.entries.mapNotNull { separator ->
        val rows = parseRows(text, separator, ImportConstants.SEP_DETECT_ROWS)
        val dominant = rows.groupingBy { it.size }.eachCount().entries.filter { it.key > 1 }
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }) ?: return@mapNotNull null
        SeparatorCandidate(separator, dominant.value.toDouble() / rows.size, dominant.key)
    }
    candidates.firstOrNull { it.separator == CsvSeparator.TAB && it.consistency >= CONSISTENT_TAB_SHARE }
        ?.let { return it.separator }
    return candidates.maxWithOrNull(compareBy<SeparatorCandidate> { it.consistency }.thenBy { it.width })?.separator
        ?: CsvSeparator.DEFAULT
}

/**
 * Komórka zapisana z powrotem do tekstu — w cudzysłowie, gdy zawiera separator,
 * nową linię (także CR) lub cudzysłów.
 */
fun encodeCell(cell: String, separator: CsvSeparator): String =
    if (cell.contains(separator.char) || cell.contains('\n') || cell.contains('\r') || cell.contains('"')) {
        csvFormat(separator).format(cell)
    } else {
        cell
    }
