package com.raycoll.app.domain.imports

object Separators {
    val TAB = "tab" to "\t"
    val SEMICOLON = "semicolon" to ";"
    val COMMA = "comma" to ","
    val PIPE = "pipe" to "|"
    val ALL = mapOf(TAB, SEMICOLON, COMMA, PIPE)
}

fun resolveSep(keyOrChar: String): String = Separators.ALL[keyOrChar] ?: keyOrChar

fun normalizeCsvText(text: String): String = text
    .removePrefix("\uFEFF")
    .replace(Regex("\\r\\n?"), "\n")

fun normalizeImportCell(value: String): String = value.trim()

fun readNextField(text: String, startPos: Int, sep: String): Pair<String, Int> {
    val sb = StringBuilder()
    var inQuotes = false
    var i = startPos
    while (i < text.length) {
        val ch = text[i]
        if (ch == '"') {
            if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                sb.append('"'); i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (!inQuotes && text.startsWith(sep, i)) {
            break
        } else if (ch == '\n' && !inQuotes) {
            break
        } else {
            sb.append(ch)
        }
        i++
    }
    return sb.toString() to i
}

fun parseFullText(text: String, sep: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    val sepLen = if (sep.isNotEmpty()) sep.length else 1
    var pos = 0
    while (pos < text.length) {
        val (field, nextPos) = readNextField(text, pos, sep)
        currentRow.add(normalizeImportCell(field))
        if (nextPos >= text.length) break
        if (text[nextPos] == '\n') {
            if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow)
            currentRow = mutableListOf()
            pos = nextPos + 1
        } else {
            pos = nextPos + sepLen
        }
    }
    if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow)
    return rows
}

fun parseCSV(text: String, sepKey: String, pageCount: Int): List<List<String>> {
    val sep = resolveSep(sepKey)
    return parseFullText(normalizeCsvText(text), sep).map { parts ->
        parts.toMutableList().also { while (it.size < pageCount) it.add("") }
    }
}

fun parseCSVRaw(text: String, sepKey: String): List<List<String>> {
    val sep = resolveSep(sepKey)
    return parseFullText(normalizeCsvText(text), sep)
}

fun serializeCSV(rows: List<List<String>>, sepKey: String): String {
    val sep = resolveSep(sepKey)
    return rows.joinToString("\n") { row ->
        row.joinToString(sep) { field ->
            var escaped = field.replace("\"", "\"\"")
            if (escaped.contains(sep) || escaped.contains('"') || escaped.contains('\n')) {
                escaped = "\"$escaped\""
            }
            escaped
        }
    }
}
