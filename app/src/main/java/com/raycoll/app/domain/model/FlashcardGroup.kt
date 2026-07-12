package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FlashcardGroup(
    val id: String,
    val name: String,
    val cards: List<Flashcard> = emptyList(),
    val activeModeId: String = "classic",
    val studyFilter: CardFilter = CardFilter.new_review,
    val cardOrder: CardOrder = CardOrder.sequential,
    val pageLanguages: List<String> = emptyList(),
    val pageNames: List<String> = emptyList(),
    val activePageCount: Int = 2,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val archivedAt: Long? = null,
)
