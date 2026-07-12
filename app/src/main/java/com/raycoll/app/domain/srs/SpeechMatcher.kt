package com.raycoll.app.domain.srs

import kotlin.math.*

fun normalizeText(text: String): String = text
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

fun levenshteinDistance(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    var prev = IntArray(n + 1) { it }
    for (i in 1..m) {
        val curr = IntArray(n + 1)
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        prev = curr
    }
    return prev[n]
}

private fun wordSimilarity(recognized: String, original: String): Double {
    val recWords = recognized.split(" ").filter { it.isNotEmpty() }
    val origWords = original.split(" ").filter { it.isNotEmpty() }
    if (origWords.isEmpty()) return if (recWords.isEmpty()) 100.0 else 0.0

    var matched = 0.0
    val used = mutableSetOf<Int>()
    for (ow in origWords) {
        var bestScore = 0.0
        var bestIdx = -1
        for (i in recWords.indices) {
            if (i in used) continue
            val maxLen = max(ow.length, recWords[i].length)
            val dist = levenshteinDistance(ow, recWords[i])
            val score = if (maxLen > 0) 1.0 - dist.toDouble() / maxLen else 1.0
            if (score > bestScore) {
                bestScore = score
                bestIdx = i
            }
        }
        if (bestIdx >= 0 && bestScore > 0.5) {
            matched += bestScore
            used.add(bestIdx)
        }
    }
    return (matched / origWords.size) * 100.0
}

fun matchSpeech(recognized: String, original: String): Int {
    val normRec = normalizeText(recognized)
    val normOrig = normalizeText(original)
    if (normOrig.isEmpty()) return if (normRec.isEmpty()) 100 else 0

    val maxLen = max(normRec.length, normOrig.length)
    val globalSim = if (maxLen > 0) (1.0 - levenshteinDistance(normRec, normOrig).toDouble() / maxLen) * 100.0 else 100.0
    val wordSim = wordSimilarity(normRec, normOrig)

    return round(max(globalSim, wordSim)).toInt()
}

fun mapMatchToRating(matchPercent: Int, successThreshold: Int? = null): Int {
    if (successThreshold != null && successThreshold in 0..100) {
        val threshold = clamp(successThreshold.toDouble(), 0.0, 100.0).roundToInt()
        if (matchPercent < threshold) return 1
        val easyThreshold = if (threshold >= 100) 100 else threshold + ceil((100.0 - threshold) / 2.0).toInt()
        return if (matchPercent >= easyThreshold) 4 else 3
    }
    return when {
        matchPercent >= 85 -> 4
        matchPercent >= 60 -> 3
        matchPercent >= 40 -> 2
        else -> 1
    }
}
