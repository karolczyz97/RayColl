package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.srs.isBuiltInModeSourceId

// migracja gościa: tylko gdy konto na tym urządzeniu jest świeże (brak lokalnego cache usera),
// a namespace gościa ma realne dane (port z useStoreBootstrap.ts)
fun shouldTriggerMigration(hasUserLocalCache: Boolean, guestHasData: Boolean): Boolean =
    !hasUserLocalCache && guestHasData

fun getGuestHasData(groups: List<FlashcardGroup>, heatmap: Map<String, Int>, modes: List<StudyMode>): Boolean {
    val liveGroups = groups.filter { it.deletedAt == null }
    if (liveGroups.isNotEmpty()) return true
    if (heatmap.values.any { it > 0 }) return true
    return modes.any { isUserAuthoredMode(it) }
}

/** Własny tryb LUB wbudowany, któremu użytkownik zmienił nazwę/kroki (port z useStoreBootstrap.ts). */
fun isUserAuthoredMode(mode: StudyMode): Boolean {
    if (!mode.isBuiltIn) return true
    val sourceId = mode.builtInSourceId ?: return true
    if (!isBuiltInModeSourceId(sourceId)) return true
    val seed = com.raycoll.app.domain.srs.createSeedModes().find { it.builtInSourceId == sourceId } ?: return true
    return mode.name != seed.name || mode.steps != seed.steps
}
