package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class StoreData(
    val groups: List<FlashcardGroup> = emptyList(),
    val studyModes: List<StudyMode> = emptyList(),
    val activityHeatmap: Map<String, Int> = emptyMap(),
    val lastSyncedAt: Long? = null,
)
