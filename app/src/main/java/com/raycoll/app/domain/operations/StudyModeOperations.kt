package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.*
import java.util.UUID

fun addStudyMode(modes: List<StudyMode>, mode: StudyMode, now: Long): List<StudyMode> =
    modes + mode.copy(id = if (mode.id.isBlank()) UUID.randomUUID().toString() else mode.id, updatedAt = now)

fun updateStudyMode(modes: List<StudyMode>, mode: StudyMode, now: Long): List<StudyMode> =
    modes.map { if (it.id == mode.id) mode.copy(updatedAt = now) else it }

fun deleteStudyMode(modes: List<StudyMode>, modeId: String, now: Long): List<StudyMode> =
    modes.map { if (it.id == modeId) it.copy(deletedAt = now) else it }

fun resetStudyMode(modes: List<StudyMode>, modeId: String, defaults: List<StudyMode>, now: Long): List<StudyMode> {
    val default = defaults.find { it.id == modeId } ?: return modes
    return modes.map { if (it.id == modeId) default.copy(id = modeId, updatedAt = now, deletedAt = null) else it }
}
