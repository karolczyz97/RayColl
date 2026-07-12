package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Flashcard(
    val id: String,
    val pages: List<String>,
    val srsState: SrsState = SrsState(),
    val contentUpdatedAt: Long = 0L,
    val srsUpdatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
