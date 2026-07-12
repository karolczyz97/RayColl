package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SrsState(
    val difficulty: Double = 5.0,
    val stability: Double = 0.0,
    val repetitions: Int = 0,
    val state: Int = 0, // 0=New, 1=Learning, 2=Review, 3=Relearning
    val lastReviewTimestamp: Long = 0L,
    val nextReviewTimestamp: Long = 0L,
)
