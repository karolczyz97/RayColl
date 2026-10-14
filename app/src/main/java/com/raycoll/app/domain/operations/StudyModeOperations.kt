package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.DEFAULT_MODE_ID
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.normalize.normalizeStudyMode
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.domain.seed.seedModeFor

fun newStudyMode(name: String, steps: List<ModeStep>, now: Long): StudyMode =
    normalizeStudyMode(StudyMode(id = Ids.newId(), name = name.trim(), steps = steps, updatedAt = now))

fun StudyMode.edited(now: Long): StudyMode = normalizeStudyMode(copy(name = name.trim(), updatedAt = now))

/**
 * Tryb, którym talia naprawdę się uczy: przypisany, a gdy go już nie ma (np. usunięty
 * własny tryb) — klasyczny, potem pierwszy dostępny. [liveModes] bez usuniętych trybów.
 * Zapisana w talii referencja się nie zmienia — to tylko jej rozwiązanie do użycia/pokazania.
 */
fun resolveStudyMode(liveModes: List<StudyMode>, modeId: String): StudyMode = liveModes.firstOrNull { it.id == modeId }
    ?: liveModes.firstOrNull { it.id == DEFAULT_MODE_ID }
    ?: liveModes.firstOrNull()
    ?: SEED_MODES.first { it.id == DEFAULT_MODE_ID }

/** Wbudowany tryb zmieniony względem wersji fabrycznej (nazwa albo kroki). */
fun StudyMode.isModifiedBuiltIn(): Boolean {
    val seed = seedModeFor(builtInSourceId)?.takeIf { isBuiltIn } ?: return false
    return name != seed.name || steps != seed.steps
}

/** Przywraca nazwę i kroki wbudowanego trybu; tryb własny zostaje bez zmian. */
fun StudyMode.resetToBuiltIn(now: Long): StudyMode {
    val seed = seedModeFor(builtInSourceId) ?: return this
    return copy(name = seed.name, steps = seed.steps, updatedAt = now)
}
