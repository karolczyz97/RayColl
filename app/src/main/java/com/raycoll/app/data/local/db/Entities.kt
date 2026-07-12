package com.raycoll.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "decks", primaryKeys = ["id", "namespace"])
data class DeckEntity(
    val id: String,
    val namespace: String = "",
    val name: String,
    val activeModeId: String = "classic",
    val studyFilter: String = "new+review",
    val cardOrder: String = "sequential",
    val pageNamesJson: String = "[]",
    val pageLanguagesJson: String = "[]",
    val activePageCount: Int = 2,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val archivedAt: Long? = null,
)

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(
        entity = DeckEntity::class,
        parentColumns = ["id", "namespace"],
        childColumns = ["deckId", "namespace"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["deckId", "namespace"])],
    primaryKeys = ["id", "namespace"]
)
data class CardEntity(
    val id: String,
    val namespace: String = "",
    val deckId: String,
    val pagesJson: String = "[]",
    val difficulty: Double = 5.0,
    val stability: Double = 0.0,
    val repetitions: Int = 0,
    val state: Int = 0,
    val lastReviewTimestamp: Long = 0,
    val nextReviewTimestamp: Long = 0,
    val contentUpdatedAt: Long = 0,
    val srsUpdatedAt: Long = 0,
    val deletedAt: Long? = null,
)

@Entity(tableName = "study_modes", primaryKeys = ["id", "namespace"])
data class StudyModeEntity(
    val id: String,
    val namespace: String = "",
    val name: String,
    val stepsJson: String = "[]",
    val isBuiltIn: Boolean = false,
    val builtInSourceId: String? = null,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

@Entity(tableName = "activity_days", primaryKeys = ["dayKey", "namespace"])
data class ActivityDayEntity(
    val dayKey: String,
    val namespace: String = "",
    val count: Int = 0,
)
