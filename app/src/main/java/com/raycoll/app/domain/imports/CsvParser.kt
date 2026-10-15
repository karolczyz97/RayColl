package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants

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

/**
 * Dzieli tekst na wiersze i komórki (cudzysłowy jak w CSV: `"a;b"`, `""` = cudzysłów).
 * Komórki są przycięte, puste wiersze pominięte; parsowanie kończy się po [maxRows] wierszach.
 */
fun parseRows(text: String, separator: CsvSeparator, maxRows: Int = Int.MAX_VALUE): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    fun endCell() {
        row += cell.toString().trim()
        cell.clear()
    }
    fun endRow() {
        endCell()
        if (row.any { it.isNotBlank() }) rows += row
        row = mutableListOf()
    }
    val normalized = normalizeCsvText(text)
    var i = 0
    while (i < normalized.length && rows.size < maxRows) {
        val char = normalized[i]
        when {
            char == '"' && inQuotes && normalized.getOrNull(i + 1) == '"' -> {
                cell.append('"')
                i++
            }

            char == '"' && (inQuotes || cell.isBlank()) -> inQuotes = !inQuotes

            !inQuotes && char == separator.char -> endCell()

            !inQuotes && char == '\n' -> endRow()

            else -> cell.append(char)
        }
        i++
    }
    if (rows.size < maxRows) endRow()
    return rows
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
        "\"" + cell.replace("\"", "\"\"") + "\""
    } else {
        cell
    }
