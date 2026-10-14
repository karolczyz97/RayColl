package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

/** Stan FSRS karty; `state`: 0 = nowa, 1 = nauka, 2 = powtórka, 3 = ponowna nauka. */
@Serializable
data class SrsState(
    val difficulty: Double = 5.0,
    val stability: Double = 0.0,
    val repetitions: Int = 0,
    val state: Int = 0,
    val lastReviewTimestamp: Long = 0,
    val nextReviewTimestamp: Long = 0
)
