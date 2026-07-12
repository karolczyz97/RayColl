package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsState
import kotlin.math.*
import kotlin.math.abs

const val DECAY = -0.5
val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0
const val MIN_INTERVAL_DAYS = 0.1
const val MAX_INTERVAL_DAYS = 3650.0

val W = doubleArrayOf(
    0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61,
)

fun createNewSrsState(): SrsState = SrsState(
    difficulty = 5.0,
    stability = 0.0,
    repetitions = 0,
    state = 0,
    lastReviewTimestamp = 0L,
    nextReviewTimestamp = 0L,
)

fun getCardCategory(state: SrsState): SrsCardCategory = when {
    state.state == 0 -> SrsCardCategory.New
    state.state == 1 || state.state == 3 -> SrsCardCategory.Learning
    state.state == 2 && state.repetitions >= 3 -> SrsCardCategory.Mastered
    state.state == 2 -> SrsCardCategory.Review
    else -> SrsCardCategory.New
}

enum class SrsCardCategory { New, Learning, Review, Mastered }

fun clamp(value: Double, min: Double, max: Double): Double {
    if (value.isNaN()) return min
    return value.coerceIn(min, max)
}

private fun initDifficulty(rating: Int): Double = clamp(W[4] - (rating - 3) * W[5], 1.0, 10.0)

private fun initStability(rating: Int): Double = max(W[rating - 1], 0.1)

private fun nextDifficulty(d: Double, rating: Int): Double {
    val newD = d - W[6] * (rating - 3)
    return clamp(W[7] * initDifficulty(3) + (1 - W[7]) * newD, 1.0, 10.0)
}

private fun retrievability(elapsedDays: Double, stability: Double): Double {
    if (stability <= 0) return 0.0
    return (1 + (FACTOR * elapsedDays) / stability).pow(DECAY)
}

private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Int): Double {
    val hardPenalty = if (rating == 2) W[15] else 1.0
    val easyBonus = if (rating == 4) W[16] else 1.0
    return s * (1 +
        exp(W[8]) *
        (11 - d) *
        s.pow(-W[9]) *
        (exp((1 - r) * W[10]) - 1) *
        hardPenalty *
        easyBonus)
}

private fun nextForgetStability(d: Double, s: Double, r: Double): Double =
    W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp((1 - r) * W[14])

fun calculateFsrs(current: SrsState, rawRating: Int, now: Long): SrsState {
    val rating = clamp(rawRating.toDouble(), 1.0, 4.0).roundToInt().coerceIn(1, 4)
    val elapsedMs = now - if (current.lastReviewTimestamp != 0L) current.lastReviewTimestamp else now
    val elapsedDays = max(elapsedMs / (1000.0 * 60 * 60 * 24), 0.0)

    val newD: Double
    val newS: Double
    val newState: Int

    if (current.state == 0 || current.stability <= 0) {
        newD = initDifficulty(rating)
        newS = initStability(rating)
        newState = if (rating == 1) 1 else 2
    } else {
        newD = nextDifficulty(current.difficulty, rating)
        val r = retrievability(elapsedDays, current.stability)
        if (rating == 1) {
            newS = nextForgetStability(newD, current.stability, r)
            newState = 3
        } else {
            newS = nextRecallStability(newD, current.stability, r, rating)
            newState = 2
        }
    }

    val clampedS = clamp(newS, MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
    val nextReview = now + (clampedS * 24 * 60 * 60 * 1000).toLong()

    return SrsState(
        difficulty = newD,
        stability = clampedS,
        repetitions = current.repetitions + 1,
        state = newState,
        lastReviewTimestamp = now,
        nextReviewTimestamp = nextReview,
    )
}
