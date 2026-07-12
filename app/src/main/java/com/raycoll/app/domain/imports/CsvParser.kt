package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.ImportConstants

val SEPARATORS = mapOf(
    "tab" to "\t", "semicolon" to ";", "comma" to ",", "pipe" to "|",
)

fun resolveSep(keyOrChar: String): String = SEPARATORS[keyOrChar] ?: keyOrChar

fun normalizeImportCell(value: String): String = value.trim()

fun normalizeCsvText(text: String): String =
    text.replace(Regex("^\\uFEFF"), "").replace(Regex("\r\n?"), "\n")

fun readNextField(text: String, startPos: Int, sep: String): Pair<String, Int> {
    val field = StringBuilder()
    var inQuotes = false
    var i = startPos
    while (i < text.length) {
        val char = text[i]
        if (char == '"') {
            if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                field.append('"'); i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (!inQuotes && sep.isNotEmpty() && text.startsWith(sep, i)) {
            break
        } else if (char == '\n' && !inQuotes) {
            break
        } else {
            field.append(char)
        }
        i++
    }
    return Pair(field.toString(), i)
}

fun parseFullText(text: String, sep: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    val sepLen = maxOf(sep.length, 1)
    var pos = 0

    while (pos < text.length) {
        val (field, nextPos) = readNextField(text, pos, sep)
        currentRow.add(normalizeImportCell(field))
        if (nextPos >= text.length) break
        if (text[nextPos] == '\n') {
            if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
            currentRow = mutableListOf()
            pos = nextPos + 1
        } else {
            pos = nextPos + sepLen
        }
    }
    if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
    return rows
}

fun parseCsv(text: String, sepKey: String, pageCount: Int): List<List<String>> {
    val sep = resolveSep(sepKey)
    return parseFullText(normalizeCsvText(text), sep).map { row ->
        val padded = row.toMutableList()
        while (padded.size < pageCount) padded.add("")
        padded
    }
}

fun detectSeparator(text: String): String {
    val normalized = normalizeCsvText(text)
    var bestKey = ImportConstants.SEPARATOR_DEFAULT
    var bestScore = 0.0

    for ((key, sep) in SEPARATORS) {
        val rows = parseFullText(normalized, sep).filter { row -> row.any { it.isNotBlank() } }.take(ImportConstants.SEP_DETECT_ROWS)
        if (rows.isEmpty()) continue
        var totalCount = 0
        var consistentLines = 0
        for (row in rows) {
            val count = row.size - 1
            if (count > 0) { totalCount += count; consistentLines++ }
        }
        val score = totalCount * (consistentLines.toDouble() / rows.size)
        if (score > bestScore) { bestScore = score; bestKey = key }
    }
    return if (bestScore > 0) bestKey else ImportConstants.SEPARATOR_DEFAULT
}
