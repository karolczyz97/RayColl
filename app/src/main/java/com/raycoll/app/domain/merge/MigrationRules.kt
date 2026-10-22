package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.isLive
import com.raycoll.app.domain.operations.isModifiedBuiltIn
import com.raycoll.app.domain.seed.seedModeFor
import java.util.UUID

/** Realne dane gościa: talia ruszona przez użytkownika, jakakolwiek aktywność albo własny tryb. */
fun guestHasData(groups: List<FlashcardGroup>, heatmap: Map<String, Int>, modes: List<StudyMode>): Boolean =
    groups.any { it.isLive && !it.isUntouchedSeed() } ||
        heatmap.values.any { it > 0 } ||
        modes.any { it.isLive && isUserAuthoredMode(it) }

/** Tryb własny albo wbudowany, któremu użytkownik zmienił nazwę lub kroki. */
fun isUserAuthoredMode(mode: StudyMode): Boolean =
    !mode.isBuiltIn || seedModeFor(mode.builtInSourceId) == null || mode.isModifiedBuiltIn()

// Talie startowe mają znaczniki czasu 0; każda edycja talii/karty albo powtórka je podbija.
fun FlashcardGroup.isUntouchedSeed(): Boolean = updatedAt == 0L && cards.isNotEmpty() && cards.all {
    it.contentUpdatedAt == 0L && it.srsUpdatedAt == 0L && it.isLive
}

/**
 * Dane gościa gotowe do dołączenia: zmienione tryby wbudowane stają się własnymi kopiami z przyrostkiem
 * [modeSuffix], a talie wskazują na te kopie; niezmienione tryby wbudowane odpadają (konto ma własne).
 * Id kopii zależy też od chwili zmiany trybu: ponowienie z pending_join trafia w tę samą kopię,
 * a przeniesienie innego gościa na to samo konto nie nadpisuje poprzedniej.
 */
fun StoreData.withBuiltInModesCopied(modeSuffix: String): StoreData {
    val remap = studyModes.filter { it.isBuiltIn && it.isModifiedBuiltIn() }.associate {
        it.id to UUID.nameUUIDFromBytes("guest-mode:${it.id}:${it.updatedAt}".toByteArray(Charsets.UTF_8)).toString()
    }
    val modes = studyModes.mapNotNull { mode ->
        if (!mode.isBuiltIn) {
            mode
        } else {
            remap[mode.id]?.let {
                mode.copy(id = it, name = mode.name + modeSuffix, isBuiltIn = false, builtInSourceId = null)
            }
        }
    }
    return copy(
        groups = groups.map {
            it.copy(activeModeId = remap[it.activeModeId] ?: it.activeModeId)
        },
        studyModes = modes
    )
}
