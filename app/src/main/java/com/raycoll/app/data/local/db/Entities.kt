package com.raycoll.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Każda tabela jest podzielona na przestrzenie (namespace): "" = gość, inaczej uid konta.
// Wartości domyślne pól nadaje model domenowy — encje zawsze powstają przez mapowanie.

@Entity(tableName = "decks", primaryKeys = ["id", "namespace"])
data class DeckEntity(
    val id: String,
    val namespace: String,
    val name: String,
    val activeModeId: String,
    val studyFilter: String,
    val cardOrder: String,
    val pageNamesJson: String,
    val pageLanguagesJson: String,
    val activePageCount: Int,
    val updatedAt: Long,
    val deletedAt: Long?,
    val archivedAt: Long?
)

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id", "namespace"],
            childColumns = ["deckId", "namespace"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId", "namespace"])],
    primaryKeys = ["id", "namespace"]
)
data class CardEntity(
    val id: String,
    val namespace: String,
    val deckId: String,
    val pagesJson: String,
    val difficulty: Double,
    val stability: Double,
    val repetitions: Int,
    val state: Int,
    val lastReviewTimestamp: Long,
    val nextReviewTimestamp: Long,
    val contentUpdatedAt: Long,
    val srsUpdatedAt: Long,
    val deletedAt: Long?
)

@Entity(tableName = "study_modes", primaryKeys = ["id", "namespace"])
data class StudyModeEntity(
    val id: String,
    val namespace: String,
    val name: String,
    val stepsJson: String,
    val isBuiltIn: Boolean,
    val builtInSourceId: String?,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Entity(tableName = "activity_days", primaryKeys = ["dayKey", "namespace"])
data class ActivityDayEntity(val dayKey: String, val namespace: String, val count: Int)
