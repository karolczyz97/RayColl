package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

/** Stan FSRS karty; `state`: jedna ze stałych [SrsStates]. */
@Serializable
data class SrsState(
    val difficulty: Double = 5.0,
    val stability: Double = 0.0,
    val repetitions: Int = 0,
    val state: Int = 0,
    val lastReviewTimestamp: Long = 0,
    val nextReviewTimestamp: Long = 0
)

/** Wartości pola [SrsState.state] (zapisane jako liczby — nie zmieniać). */
object SrsStates {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3
    val VALID = NEW..RELEARNING
}
