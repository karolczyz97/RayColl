package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Flashcard(
    val id: String,
    val pages: List<String>,
    val srsState: SrsState,
    val contentUpdatedAt: Long = 0,
    val srsUpdatedAt: Long = 0,
    val deletedAt: Long? = null
)

/** Karta bez tombstone'a. */
val Flashcard.isLive: Boolean get() = deletedAt == null
