package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsConstants
import kotlin.math.ceil

fun archiveCountdown(group: FlashcardGroup, now: Long): Int {
    val archivedAt = group.archivedAt ?: return -1
    val retentionMs = ArchiveConstants.RETENTION_DAYS * SrsConstants.DAY_MS
    val deleteAt = archivedAt + retentionMs
    val remaining = deleteAt - now
    return if (remaining <= 0) 0 else ceil(remaining.toDouble() / SrsConstants.DAY_MS).toInt()
}

fun selectActiveGroups(groups: List<FlashcardGroup>): List<FlashcardGroup> =
    groups.filter { it.deletedAt == null && it.archivedAt == null }

fun selectArchivedGroups(groups: List<FlashcardGroup>): List<FlashcardGroup> =
    groups.filter { it.deletedAt == null && it.archivedAt != null }.sortedByDescending { it.archivedAt ?: 0 }
