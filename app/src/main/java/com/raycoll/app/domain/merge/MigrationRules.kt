package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.seed.seedModeFor

/**
 * Dialog migracji danych gościa pokazujemy tylko, gdy konto jest na tym urządzeniu
 * nowe (brak lokalnej kopii) i gość ma realne dane.
 */
fun shouldTriggerMigration(hasUserLocalCache: Boolean, guestHasData: Boolean): Boolean =
    !hasUserLocalCache && guestHasData

fun getGuestHasData(groups: List<FlashcardGroup>, heatmap: Map<String, Int>, modes: List<StudyMode>): Boolean =
    groups.any { it.deletedAt == null } ||
        heatmap.values.any { it > 0 } ||
        modes.any(::isUserAuthoredMode)

/** Tryb własny albo wbudowany, któremu użytkownik zmienił nazwę lub kroki. */
fun isUserAuthoredMode(mode: StudyMode): Boolean {
    if (!mode.isBuiltIn) return true
    val seed = seedModeFor(mode.builtInSourceId) ?: return true
    return mode.name != seed.name || mode.steps != seed.steps
}
