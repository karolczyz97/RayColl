package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.normalize.normalizeStudyMode
import com.raycoll.app.domain.srs.createSeedModes

fun addStudyMode(modes: List<StudyMode>, name: String, steps: List<com.raycoll.app.domain.model.ModeStep>, now: Long): Pair<List<StudyMode>, String> {
    val mode = normalizeStudyMode(StudyMode(id = com.raycoll.app.core.Ids.newId(), name = name, steps = steps, updatedAt = now))
    return Pair(modes + mode, mode.id)
}

fun updateStudyMode(modes: List<StudyMode>, mode: StudyMode, now: Long): List<StudyMode> =
    modes.map { if (it.id == mode.id) normalizeStudyMode(mode.copy(updatedAt = now)) else it }

fun deleteStudyMode(modes: List<StudyMode>, modeId: String, now: Long): List<StudyMode> =
    modes.map { if (it.id == modeId) it.copy(deletedAt = now) else it }

fun resetStudyMode(modes: List<StudyMode>, modeId: String, now: Long): List<StudyMode> {
    val seedModes = createSeedModes()
    return modes.map { m ->
        if (m.id != modeId) m
        else {
            val seed = seedModes.find { it.builtInSourceId == m.builtInSourceId }
                ?: return@map m
            m.copy(name = seed.name, steps = seed.steps, updatedAt = now)
        }
    }
}
