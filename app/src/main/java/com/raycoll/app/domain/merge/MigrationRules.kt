package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.srs.isBuiltInModeSourceId

fun shouldTriggerMigration(hasUserLocalCache: Boolean, guestHasData: Boolean): Boolean =
    hasUserLocalCache && guestHasData

fun getGuestHasData(groups: List<FlashcardGroup>, heatmap: Map<String, Int>, modes: List<StudyMode>): Boolean {
    val liveGroups = groups.filter { it.deletedAt == null }
    if (liveGroups.isNotEmpty()) return true
    if (heatmap.values.any { it > 0 }) return true
    return modes.any { isUserAuthoredMode(it) }
}

fun isUserAuthoredMode(mode: StudyMode): Boolean {
    if (!mode.isBuiltIn) return true
    val sourceId = mode.builtInSourceId ?: return true
    if (!isBuiltInModeSourceId(sourceId)) return true
    return false
}
