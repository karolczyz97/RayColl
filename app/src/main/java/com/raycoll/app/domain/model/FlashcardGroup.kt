package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FlashcardGroup(
    val id: String,
    val name: String,
    val cards: List<Flashcard>,
    val activeModeId: String = "classic",
    val studyFilter: String = "new+review",
    val cardOrder: String = "sequential",
    val pageLanguages: List<String> = emptyList(),
    val pageNames: List<String> = emptyList(),
    val activePageCount: Int = 2,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val archivedAt: Long? = null,
)
