package com.raycoll.app.domain.selectors

import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.FlashcardGroup
import java.time.temporal.ChronoUnit

/** Dni kalendarzowe do usunięcia; null dla talii niezarchiwizowanej. */
fun archiveDaysLeft(group: FlashcardGroup, now: Long): Int? {
    val archivedAt = group.archivedAt ?: return null
    val expiresAt = archivedAt + ArchiveConstants.RETENTION_MS
    return ChronoUnit.DAYS.between(LocalDates.localDate(now), LocalDates.localDate(expiresAt)).toInt().coerceAtLeast(0)
}
