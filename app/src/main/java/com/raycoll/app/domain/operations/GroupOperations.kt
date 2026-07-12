package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.normalize.clampActivePageCount
import com.raycoll.app.domain.normalize.padPageMetadata
import com.raycoll.app.domain.srs.createNewSrsState
import java.util.UUID

fun addGroup(groups: List<FlashcardGroup>, name: String, languages: List<String>, pageNames: List<String>, now: Long): Pair<List<FlashcardGroup>, String> {
    val id = UUID.randomUUID().toString()
    val g = FlashcardGroup(
        id = id, name = name.trim(),
        pageLanguages = languages, pageNames = pageNames,
        activePageCount = clampActivePageCount(pageNames.size, 5),
        updatedAt = now,
    )
    return groups + g to id
}

fun updateGroup(groups: List<FlashcardGroup>, group: FlashcardGroup, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != group.id) g
        else {
            val incomingIds = group.cards.map { it.id }.toSet()
            val tombstoned = g.cards.filter { it.deletedAt != null && it.id !in incomingIds }
            group.copy(updatedAt = now, cards = group.cards + tombstoned)
        }
    }

fun deleteGroup(groups: List<FlashcardGroup>, groupId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(deletedAt = now, archivedAt = null) else it }

fun archiveGroup(groups: List<FlashcardGroup>, groupId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(archivedAt = now, updatedAt = now) else it }

fun restoreGroup(groups: List<FlashcardGroup>, groupId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(archivedAt = null, updatedAt = now) else it }

fun purgeExpiredArchives(groups: List<FlashcardGroup>, now: Long, retentionMs: Long = 14 * 24 * 60 * 60 * 1000L): Pair<List<FlashcardGroup>, Boolean> {
    var changed = false
    val next = groups.map { g ->
        val archivedAt = g.archivedAt ?: 0L
        val deletedAt = g.deletedAt ?: 0L
        if (archivedAt > 0 && deletedAt <= 0 && now - archivedAt >= retentionMs) {
            changed = true
            g.copy(deletedAt = now)
        } else g
    }
    return next to changed
}

fun setVisiblePageCount(groups: List<FlashcardGroup>, groupId: String, count: Int, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else {
            val clamped = clampActivePageCount(count, 5)
            val (names, langs) = padPageMetadata(g.pageNames, g.pageLanguages, clamped)
            g.copy(activePageCount = clamped, pageNames = names, pageLanguages = langs, updatedAt = now)
        }
    }

fun setStudyFilter(groups: List<FlashcardGroup>, groupId: String, filter: CardFilter, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(studyFilter = filter, updatedAt = now) else it }

fun setCardOrder(groups: List<FlashcardGroup>, groupId: String, order: CardOrder, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(cardOrder = order, updatedAt = now) else it }

fun setActiveStudyMode(groups: List<FlashcardGroup>, groupId: String, modeId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(activeModeId = modeId, updatedAt = now) else it }

fun addGroupWithCards(
    groups: List<FlashcardGroup>, name: String, languages: List<String>,
    pageNames: List<String>, cardsData: List<List<String>>, now: Long,
): Pair<List<FlashcardGroup>, String> {
    val id = UUID.randomUUID().toString()
    val cards = cardsData.map { Flashcard(id = UUID.randomUUID().toString(), pages = it, srsState = createNewSrsState(), contentUpdatedAt = now) }
    val g = FlashcardGroup(
        id = id, name = name.trim(), pageLanguages = languages, pageNames = pageNames,
        activePageCount = clampActivePageCount(pageNames.size, 5), cards = cards, updatedAt = now,
    )
    return groups + g to id
}
