package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.DEFAULT_MODE_ID
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.normalize.normalizeStudyMode
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.domain.seed.seedModeFor
import java.text.Collator

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

/** Kolejność na listach: tryby wbudowane w ustalonej kolejności, potem własne alfabetycznie. */
fun List<StudyMode>.inDisplayOrder(): List<StudyMode> {
    val collator = Collator.getInstance()
    return sortedWith(compareBy(::builtInPosition).thenComparator { a, b -> collator.compare(a.name, b.name) })
}

private fun builtInPosition(mode: StudyMode): Int {
    val builtIn = BuiltInMode.fromId(mode.builtInSourceId)?.takeIf { mode.isBuiltIn }
    return builtIn?.ordinal ?: BuiltInMode.entries.size
}

/** Wbudowany tryb zmieniony względem wersji fabrycznej (nazwa albo kroki). */
fun StudyMode.isModifiedBuiltIn(): Boolean {
    if (!isBuiltIn) return false
    val seed = seedModeFor(builtInSourceId) ?: return false
    return name != seed.name || steps != seed.steps
}

/** Przywraca nazwę i kroki wbudowanego trybu; tryb własny zostaje bez zmian. */
fun StudyMode.resetToBuiltIn(now: Long): StudyMode {
    val seed = seedModeFor(builtInSourceId) ?: return this
    return copy(name = seed.name, steps = seed.steps, updatedAt = now)
}
