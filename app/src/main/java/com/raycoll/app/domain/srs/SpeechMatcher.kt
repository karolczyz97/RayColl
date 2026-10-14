package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.MatchConstants
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun normalizeText(text: String): String = text.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

fun levenshteinDistance(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    if (m == 0) return n
    if (n == 0) return m
    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)
    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev
        prev = curr
        curr = tmp
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
            if (used.contains(i)) continue
            val maxLen = maxOf(ow.length, recWords[i].length)
            val dist = levenshteinDistance(ow, recWords[i])
            val score = if (maxLen > 0) 1.0 - dist.toDouble() / maxLen else 1.0
            if (score > bestScore) {
                bestScore = score
                bestIdx = i
            }
        }
        if (bestIdx >= 0 && bestScore > MatchConstants.WORD_MATCH_MIN) {
            matched += bestScore
            used.add(bestIdx)
        }
    }
    return matched / origWords.size * 100
}

fun matchSpeech(recognized: String, original: String): Int {
    val normRec = normalizeText(recognized)
    val normOrig = normalizeText(original)
    if (normOrig.isEmpty()) return if (normRec.isEmpty()) 100 else 0

    val maxLen = maxOf(normRec.length, normOrig.length)
    val globalSim = if (maxLen > 0) (1 - levenshteinDistance(normRec, normOrig).toDouble() / maxLen) * 100 else 100.0
    val wordSim = wordSimilarity(normRec, normOrig)
    return maxOf(globalSim, wordSim).roundToInt()
}

fun mapMatchToRating(matchPercent: Int, successThreshold: Int? = null): Int {
    if (successThreshold != null) {
        val threshold = successThreshold.coerceIn(0, 100)
        if (matchPercent < threshold) return Ratings.AGAIN
        val easyThreshold = if (threshold >= 100) 100 else threshold + ceil((100 - threshold) / 2.0).toInt()
        return if (matchPercent >= easyThreshold) Ratings.EASY else Ratings.GOOD
    }
    return when {
        matchPercent >= MatchConstants.MATCH_EASY -> Ratings.EASY
        matchPercent >= MatchConstants.MATCH_GOOD -> Ratings.GOOD
        matchPercent >= MatchConstants.MATCH_HARD -> Ratings.HARD
        else -> Ratings.AGAIN
    }
}
