package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.SrsStates
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

    if (current.state == SrsStates.NEW || current.stability <= 0) {
        newD = initDifficulty(w, rating)
        newS = initStability(w, rating)
        newState = if (rating == Ratings.AGAIN) SrsStates.LEARNING else SrsStates.REVIEW
    } else {
        newD = nextDifficulty(w, current.difficulty, rating)
        val r = retrievability(elapsedDays, current.stability)
        if (rating == Ratings.AGAIN) {
            newS = minOf(current.stability, nextForgetStability(w, current.difficulty, current.stability, r))
            newState = SrsStates.RELEARNING
        } else {
            newS = nextRecallStability(w, current.difficulty, current.stability, r, rating)
            newState = SrsStates.REVIEW
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

// rating - 3: odchylenie od oceny "dobrze" (stała formuły FSRS)
private fun initDifficulty(w: List<Double>, rating: Int): Double =
    clamp(w[4] - (rating - 3) * w[5], SrsConstants.DIFFICULTY_MIN, SrsConstants.DIFFICULTY_MAX)

private fun initStability(w: List<Double>, rating: Int): Double = max(w[rating - 1], SrsConstants.MIN_INTERVAL_DAYS)

private fun nextDifficulty(w: List<Double>, d: Double, rating: Int): Double {
    val newD = d - w[6] * (rating - 3)
    return clamp(
        w[7] * initDifficulty(w, 3) + (1 - w[7]) * newD,
        SrsConstants.DIFFICULTY_MIN,
        SrsConstants.DIFFICULTY_MAX
    )
}

private fun retrievability(elapsedDays: Double, stability: Double): Double {
    if (stability <= 0) return 0.0
    return (1 + SrsConstants.FACTOR * elapsedDays / stability).pow(SrsConstants.DECAY)
}

// 11 - d: stała formuły FSRS (górna granica trudności + 1)
private fun nextRecallStability(w: List<Double>, d: Double, s: Double, r: Double, rating: Int): Double {
    val hardPenalty = if (rating == Ratings.HARD) w[15] else 1.0
    val easyBonus = if (rating == Ratings.EASY) w[16] else 1.0
    return s * (
        1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) *
            (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus
        )
}

private fun nextForgetStability(w: List<Double>, d: Double, s: Double, r: Double): Double =
    w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])

private fun clamp(value: Double, min: Double, max: Double): Double =
    if (value.isNaN()) min else maxOf(min, minOf(max, value))
