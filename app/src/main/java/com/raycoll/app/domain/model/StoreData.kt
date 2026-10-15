package com.raycoll.app.domain.model

import kotlinx.serialization.Serializable

/** Wszystkie dane jednego użytkownika — format kopii zapasowej i jednostka synchronizacji. */
@Serializable
data class StoreData(
    val groups: List<FlashcardGroup> = emptyList(),
    val studyModes: List<StudyMode> = emptyList(),
    val activityHeatmap: Map<String, Int> = emptyMap(),
    /** Nowa generacja zastępuje całe konto, również aktywność i tryby wbudowane. */
    val replacementAt: Long = 0,
    val replacementId: String = ""
)
