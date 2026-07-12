package com.raycoll.app.domain.imports

fun detectSeparator(text: String): String {
    val normalized = normalizeCsvText(text)
    var bestKey = "semicolon"
    var bestScore = 0.0

    for ((key, sep) in Separators.ALL) {
        val rows = parseFullText(normalized, sep)
            .filter { it.any { f -> f.trim().isNotEmpty() } }
            .take(5)
        if (rows.isEmpty()) continue

        val totalCount = rows.sumOf { (it.size - 1).coerceAtLeast(0) }
        val consistentLines = rows.count { it.size > 1 }
        val score = totalCount * (consistentLines.toDouble() / rows.size)
        if (score > bestScore) { bestScore = score; bestKey = key }
    }
    return if (bestScore > 0) bestKey else "semicolon"
}
