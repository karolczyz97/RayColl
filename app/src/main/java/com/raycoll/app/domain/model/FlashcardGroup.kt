package com.raycoll.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FlashcardGroup(
    val id: String,
    val name: String,
    val cards: List<Flashcard>,
    val activeModeId: String = DEFAULT_MODE_ID,
    val studyFilter: StudyFilter = StudyFilter.DEFAULT,
    val cardOrder: CardOrder = CardOrder.DEFAULT,
    val pageLanguages: List<String> = emptyList(),
    val pageNames: List<String> = emptyList(),
    val activePageCount: Int = PageLimits.MIN_PAGE_COUNT,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val archivedAt: Long? = null
)

/** Indeksy aktywnych stron talii. */
val FlashcardGroup.activePageRange: IntRange get() = 0 until activePageCount

/** Talia bez tombstone'a. */
val FlashcardGroup.isLive: Boolean get() = deletedAt == null

/** Które karty trafiają do sesji nauki. */
@Serializable
enum class StudyFilter(val key: String) {
    @SerialName("new+review")
    NEW_AND_REVIEW("new+review"),

    @SerialName("all")
    ALL("all"),

    @SerialName("new")
    NEW("new"),

    @SerialName("review")
    REVIEW("review");

    companion object {
        val DEFAULT = NEW_AND_REVIEW
    }
}

/** Kolejność kart w sesji. */
@Serializable
enum class CardOrder(val key: String) {
    @SerialName("sequential")
    SEQUENTIAL("sequential"),

    @SerialName("random")
    RANDOM("random"),

    @SerialName("hardest")
    HARDEST("hardest");

    companion object {
        val DEFAULT = SEQUENTIAL
    }
}
