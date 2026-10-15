package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.MatchConstants
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.StudyConstants
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.apache.commons.text.similarity.LevenshteinDistance

private val NON_WORD_CHARS = Regex("[^\\p{L}\\p{N}\\s]")
private val WHITESPACE = Regex("\\s+")

private fun normalizeText(text: String): String = text.lowercase()
    .replace(NON_WORD_CHARS, "")
    .replace(WHITESPACE, " ")
    .trim()

private val levenshteinDistance = LevenshteinDistance.getDefaultInstance()

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
            val score = 1.0 - levenshteinDistance.apply(ow, recWords[i]).toDouble() / maxLen
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
    val globalSim = (1 - levenshteinDistance.apply(normRec, normOrig).toDouble() / maxLen) * 100
    val wordSim = wordSimilarity(normRec, normOrig)
    return maxOf(globalSim, wordSim).roundToInt()
}

/** Ocena z dopasowania: poniżej progu = 1, od progu = 3, w górnej połowie przedziału do 100% = 4. */
fun mapMatchToRating(matchPercent: Int, successThreshold: Int): Int {
    val threshold = successThreshold.coerceIn(0, StudyConstants.MAX_THRESHOLD_PERCENT)
    if (matchPercent < threshold) return Ratings.AGAIN
    val easyThreshold = threshold + ceil((StudyConstants.MAX_THRESHOLD_PERCENT - threshold) / 2.0).toInt()
    return if (matchPercent >= easyThreshold) Ratings.EASY else Ratings.GOOD
}
