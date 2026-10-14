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
fun normalizeCsvText(text: String): String = text.removePrefix("\uFEFF").replace(LINE_BREAKS, "\n")

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

/** Separator, który daje najwięcej spójnych kolumn w pierwszych wierszach. */
fun detectSeparator(text: String): CsvSeparator {
    val candidates = CsvSeparator.entries.map { separator ->
        separator to parseRows(text, separator, ImportConstants.SEP_DETECT_ROWS)
    }
    val best = candidates.maxByOrNull { (_, rows) ->
        if (rows.isEmpty()) return@maxByOrNull 0.0
        val splitRows = rows.filter { it.size > 1 }
        splitRows.sumOf { it.size - 1 } * (splitRows.size.toDouble() / rows.size)
    }
    return best?.takeIf { (_, rows) -> rows.any { it.size > 1 } }?.first ?: CsvSeparator.DEFAULT
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
