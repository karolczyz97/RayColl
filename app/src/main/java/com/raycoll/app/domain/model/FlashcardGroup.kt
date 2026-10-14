package com.raycoll.app.domain.model

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

/** Które karty trafiają do sesji nauki. */
@Serializable(with = StudyFilter.Serializer::class)
enum class StudyFilter(override val key: String) : KeyedEnum {
    NEW_AND_REVIEW("new+review"),
    ALL("all"),
    NEW("new"),
    REVIEW("review");

    companion object {
        val DEFAULT = NEW_AND_REVIEW
        fun fromKey(key: String?): StudyFilter = keyedEnumOrNull(entries, key) ?: DEFAULT
    }

    object Serializer : KeyedEnumSerializer<StudyFilter>("StudyFilter", entries, DEFAULT)
}

/** Kolejność kart w sesji. */
@Serializable(with = CardOrder.Serializer::class)
enum class CardOrder(override val key: String) : KeyedEnum {
    SEQUENTIAL("sequential"),
    RANDOM("random"),
    HARDEST("hardest");

    companion object {
        val DEFAULT = SEQUENTIAL
        fun fromKey(key: String?): CardOrder = keyedEnumOrNull(entries, key) ?: DEFAULT
    }

    object Serializer : KeyedEnumSerializer<CardOrder>("CardOrder", entries, DEFAULT)
}
