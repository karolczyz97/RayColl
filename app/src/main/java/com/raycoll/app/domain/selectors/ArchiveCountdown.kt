package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.FlashcardGroup
import kotlin.math.max

fun archiveDaysLeft(group: FlashcardGroup, now: Long, retentionMs: Long = 14L * 24 * 60 * 60 * 1000): Int {
    val archivedAt = group.archivedAt ?: return 0
    val elapsed = now - archivedAt
    val remaining = retentionMs - elapsed
    return max(0, (remaining / (24 * 60 * 60 * 1000)).toInt())
}

fun archiveAutoDeleteLabel(daysLeft: Int): String = when {
    daysLeft <= 0 -> "today"
    daysLeft == 1 -> "tomorrow"
    else -> "${daysLeft}d"
}
