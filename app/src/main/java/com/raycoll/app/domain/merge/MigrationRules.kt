package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.isModifiedBuiltIn
import com.raycoll.app.domain.seed.seedModeFor

/**
 * Dialog migracji danych gościa pokazujemy tylko, gdy konto jest na tym urządzeniu
 * nowe (brak lokalnej kopii) i gość ma realne dane.
 */
fun shouldTriggerMigration(hasUserLocalCache: Boolean, guestHasData: Boolean): Boolean =
    !hasUserLocalCache && guestHasData

/** Realne dane gościa: talia ruszona przez użytkownika, jakakolwiek aktywność albo własny tryb. */
fun getGuestHasData(groups: List<FlashcardGroup>, heatmap: Map<String, Int>, modes: List<StudyMode>): Boolean =
    groups.any { it.deletedAt == null && !it.isUntouchedSeed() } ||
        heatmap.values.any { it > 0 } ||
        modes.any { it.deletedAt == null && isUserAuthoredMode(it) }

/** Tryb własny albo wbudowany, któremu użytkownik zmienił nazwę lub kroki. */
fun isUserAuthoredMode(mode: StudyMode): Boolean =
    !mode.isBuiltIn || seedModeFor(mode.builtInSourceId) == null || mode.isModifiedBuiltIn()

// Talie startowe mają znaczniki czasu 0; każda edycja talii/karty albo powtórka je podbija.
private fun FlashcardGroup.isUntouchedSeed(): Boolean =
    updatedAt == 0L && cards.isNotEmpty() && cards.all { it.contentUpdatedAt == 0L && it.srsUpdatedAt == 0L }
