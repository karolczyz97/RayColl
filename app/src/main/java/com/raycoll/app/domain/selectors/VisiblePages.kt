package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.*

fun getVisiblePages(card: Flashcard, group: FlashcardGroup): List<String> {
    val padded = card.pages.toMutableList()
    while (padded.size < group.activePageCount) padded.add("")
    return padded.take(group.activePageCount)
}

fun getVisiblePageNames(group: FlashcardGroup): List<String> =
    group.pageNames.take(group.activePageCount)

fun groupHasHiddenPages(group: FlashcardGroup): Boolean =
    group.pageNames.size > group.activePageCount

fun getAllPages(card: Flashcard, group: FlashcardGroup): List<String> {
    val padded = card.pages.toMutableList()
    while (padded.size < group.pageNames.size) padded.add("")
    return padded
}

fun selectActiveGroups(groups: List<FlashcardGroup>): List<FlashcardGroup> =
    groups
        .filter { it.deletedAt == null }
        .filter { (it.archivedAt ?: 0L) == 0L }
        .map { g -> g.copy(cards = g.cards.filter { it.deletedAt == null }) }

fun selectArchivedGroups(groups: List<FlashcardGroup>): List<FlashcardGroup> =
    groups
        .filter { it.deletedAt == null }
        .filter { (it.archivedAt ?: 0L) > 0L }
        .map { g -> g.copy(cards = g.cards.filter { it.deletedAt == null }) }
        .sortedByDescending { it.archivedAt ?: 0L }

fun selectLiveStudyModes(modes: List<StudyMode>): List<StudyMode> =
    modes.filter { it.deletedAt == null }
