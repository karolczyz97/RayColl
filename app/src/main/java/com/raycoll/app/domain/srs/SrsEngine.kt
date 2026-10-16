package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.SrsStates
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Harmonogram FSRS-6, przepisany ręcznie i sprawdzony z fsrs-rs (src/model_v6.rs) oraz py-fsrs 6.3.2.
 * Bez kroków nauki: każda ocena, także tego samego dnia, idzie wzorami długoterminowymi
 * z ułamkowym czasem od ostatniej powtórki.
 */
fun calculateFsrs(current: SrsState, rawRating: Int, now: Long): SrsState {
    val w = SrsConstants.W
    val rating = rawRating.coerceIn(Ratings.MIN, Ratings.MAX)
    val lastReview = current.lastReviewTimestamp
    val elapsedMs = if (lastReview == 0L) 0L else now - lastReview
    val elapsedDays = max(elapsedMs.toDouble() / SrsConstants.DAY_MS, 0.0)

    val newD: Double
    val newS: Double
    val newState: Int

    if (current.state == SrsStates.NEW || current.stability <= 0) {
        newD = clampDifficulty(initDifficulty(w, rating))
        newS = initStability(w, rating)
        newState = if (rating == Ratings.AGAIN) SrsStates.LEARNING else SrsStates.REVIEW
    } else {
        val d = clampDifficulty(current.difficulty)
        val s = current.stability
        val r = retrievability(elapsedDays, s)
        newD = nextDifficulty(w, d, rating)
        if (rating == Ratings.AGAIN) {
            newS = nextForgetStability(w, d, s, r)
            newState = SrsStates.RELEARNING
        } else {
            newS = nextRecallStability(w, d, s, r, rating)
            newState = SrsStates.REVIEW
        }
    }

    // clamp zamiast coerceIn: NaN daje minimum, a nie trafia do nextReview i zapisu
    val clampedS = clamp(newS, SrsConstants.MIN_INTERVAL_DAYS, SrsConstants.MAX_INTERVAL_DAYS)
    val nextReview = now + (nextIntervalDays(clampedS) * SrsConstants.DAY_MS).toLong()

    return current.copy(
        difficulty = newD,
        stability = clampedS,
        repetitions = current.repetitions + 1,
        state = newState,
        lastReviewTimestamp = now,
        nextReviewTimestamp = nextReview
    )
}

/** D0(G) = w4 − e^(w5·(G−1)) + 1; bez przycięcia, bo cel średniej rewersji D0(4) wychodzi poniżej 1. */
private fun initDifficulty(w: List<Double>, rating: Int): Double = w[4] - exp(w[5] * (rating - 1)) + 1

private fun initStability(w: List<Double>, rating: Int): Double = max(w[rating - 1], SrsConstants.MIN_INTERVAL_DAYS)

// rating - 3: odchylenie od oceny „dobrze”; zmiana słabnie liniowo im bliżej maksymalnej trudności
private fun nextDifficulty(w: List<Double>, d: Double, rating: Int): Double {
    val deltaD = -w[6] * (rating - 3)
    val dampedD = d + deltaD * (SrsConstants.DIFFICULTY_MAX - d) / (SrsConstants.DIFFICULTY_MAX - 1)
    return clampDifficulty(w[7] * initDifficulty(w, Ratings.EASY) + (1 - w[7]) * dampedD)
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

/** Po pomyłce stabilność nie może przekroczyć S / e^(w17·w18) — górna granica z FSRS-6. */
private fun nextForgetStability(w: List<Double>, d: Double, s: Double, r: Double): Double {
    val longTerm = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])
    return minOf(longTerm, s / exp(w[17] * w[18]))
}

private fun nextIntervalDays(stability: Double): Double {
    val interval = stability / SrsConstants.FACTOR *
        (SrsConstants.DESIRED_RETENTION.pow(1.0 / SrsConstants.DECAY) - 1)
    return clamp(interval, SrsConstants.MIN_INTERVAL_DAYS, SrsConstants.MAX_INTERVAL_DAYS)
}

private fun clampDifficulty(value: Double): Double =
    clamp(value, SrsConstants.DIFFICULTY_MIN, SrsConstants.DIFFICULTY_MAX)

private fun clamp(value: Double, min: Double, max: Double): Double =
    if (value.isNaN()) min else maxOf(min, minOf(max, value))
