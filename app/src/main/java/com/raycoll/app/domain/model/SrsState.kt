package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SrsState(
    val difficulty: Double = 5.0,
    val stability: Double = 0.0,
    val repetitions: Int = 0,
    val state: Int = 0,
    val lastReviewTimestamp: Long = 0,
    val nextReviewTimestamp: Long = 0,
)

fun createNewSrsState(): SrsState = SrsState(
    difficulty = 5.0,
    stability = 0.0,
    repetitions = 0,
    state = 0,
    lastReviewTimestamp = 0,
    nextReviewTimestamp = 0,
)
