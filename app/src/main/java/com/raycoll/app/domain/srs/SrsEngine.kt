package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

fun calculateFsrs(current: SrsState, rawRating: Int, now: Long): SrsState {
    val w = SrsConstants.W
    val rating = rawRating.coerceIn(Ratings.MIN, Ratings.MAX)
    val lastReview = current.lastReviewTimestamp
    val elapsedMs = now - (if (lastReview != 0L) lastReview else now)
    val elapsedDays = max(elapsedMs.toDouble() / SrsConstants.DAY_MS, 0.0)

    val newD: Double
    val newS: Double
    val newState: Int

    if (current.state == 0 || current.stability <= 0) {
        newD = initDifficulty(w, rating)
        newS = initStability(w, rating)
        newState = if (rating == Ratings.AGAIN) 1 else 2
    } else {
        newD = nextDifficulty(w, current.difficulty, rating)
        val r = retrievability(elapsedDays, current.stability)
        if (rating == Ratings.AGAIN) {
            newS = minOf(current.stability, nextForgetStability(w, current.difficulty, current.stability, r))
            newState = 3
        } else {
            newS = nextRecallStability(w, current.difficulty, current.stability, r, rating)
            newState = 2
        }
    }

    // clamp zamiast coerceIn: NaN daje minimum, a nie trafia do nextReview i zapisu
    val clampedS = clamp(newS, SrsConstants.MIN_INTERVAL_DAYS, SrsConstants.MAX_INTERVAL_DAYS)
    val nextReview = now + (clampedS * SrsConstants.DAY_MS).toLong()

    return current.copy(
        difficulty = newD,
        stability = clampedS,
        repetitions = current.repetitions + 1,
        state = newState,
        lastReviewTimestamp = now,
        nextReviewTimestamp = nextReview
    )
}

private fun initDifficulty(w: DoubleArray, rating: Int): Double = clamp(w[4] - (rating - 3) * w[5], 1.0, 10.0)

private fun initStability(w: DoubleArray, rating: Int): Double = max(w[rating - 1], 0.1)

private fun nextDifficulty(w: DoubleArray, d: Double, rating: Int): Double {
    val newD = d - w[6] * (rating - 3)
    return clamp(w[7] * initDifficulty(w, 3) + (1 - w[7]) * newD, 1.0, 10.0)
}

private fun retrievability(elapsedDays: Double, stability: Double): Double {
    if (stability <= 0) return 0.0
    return (1 + SrsConstants.FACTOR * elapsedDays / stability).pow(SrsConstants.DECAY)
}

private fun nextRecallStability(w: DoubleArray, d: Double, s: Double, r: Double, rating: Int): Double {
    val hardPenalty = if (rating == Ratings.HARD) w[15] else 1.0
    val easyBonus = if (rating == Ratings.EASY) w[16] else 1.0
    return s * (
        1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) *
            (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus
        )
}

private fun nextForgetStability(w: DoubleArray, d: Double, s: Double, r: Double): Double =
    w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])

private fun clamp(value: Double, min: Double, max: Double): Double =
    if (value.isNaN()) min else maxOf(min, minOf(max, value))
