package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.normalize.DEFAULT_CARD_ORDER
import com.raycoll.app.domain.normalize.DEFAULT_STUDY_FILTER
import com.raycoll.app.domain.normalize.normalizeCardOrder
import com.raycoll.app.domain.normalize.normalizeStudyFilter
import com.raycoll.app.domain.normalize.padPageMetadata

fun addGroup(groups: List<FlashcardGroup>, name: String, languages: List<String>, pageNames: List<String>, now: Long): Pair<List<FlashcardGroup>, String> {
    val g = createGroupObject(name, languages, pageNames, emptyList(), now)
    return Pair(groups + g, g.id)
}

fun addGroupWithCards(groups: List<FlashcardGroup>, name: String, languages: List<String>, pageNames: List<String>, cards: List<Flashcard>, now: Long): Pair<List<FlashcardGroup>, String> {
    val g = createGroupObject(name, languages, pageNames, cards, now)
    return Pair(groups + g, g.id)
}

private fun createGroupObject(name: String, languages: List<String>, pageNames: List<String>, cards: List<Flashcard>, now: Long): FlashcardGroup = FlashcardGroup(
    id = Ids.newId(),
    name = name.trim(),
    cards = cards,
    activeModeId = "classic",
    studyFilter = DEFAULT_STUDY_FILTER,
    cardOrder = DEFAULT_CARD_ORDER,
    pageLanguages = languages,
    pageNames = pageNames,
    activePageCount = PageLimits.clampActivePageCount(pageNames.size, PageLimits.MAX_VISIBLE_PAGE_COUNT),
    updatedAt = now,
)

fun updateGroup(groups: List<FlashcardGroup>, group: FlashcardGroup, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != group.id) g
        else {
            val incomingCardIds = group.cards.map { it.id }.toSet()
            val tombstonedCards = g.cards.filter { it.deletedAt != null && it.id !in incomingCardIds }
            group.copy(updatedAt = now, cards = group.cards + tombstonedCards)
        }
    }

fun deleteGroup(groups: List<FlashcardGroup>, groupId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(deletedAt = now, archivedAt = null) else it }

fun archiveGroup(groups: List<FlashcardGroup>, groupId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(archivedAt = now, updatedAt = now) else it }

fun restoreGroup(groups: List<FlashcardGroup>, groupId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(archivedAt = null, updatedAt = now) else it }

fun purgeExpiredArchives(groups: List<FlashcardGroup>, now: Long, retentionMs: Long): Pair<List<FlashcardGroup>, Boolean> {
    var changed = false
    val next = groups.map { g ->
        val archivedAt = g.archivedAt ?: 0
        val deletedAt = g.deletedAt ?: 0
        if (archivedAt > 0 && deletedAt <= 0 && now - archivedAt >= retentionMs) {
            changed = true
            g.copy(deletedAt = now)
        } else g
    }
    return Pair(next, changed)
}

fun setVisiblePageCount(groups: List<FlashcardGroup>, groupId: String, count: Int, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else {
            val clamped = PageLimits.clampActivePageCount(count, PageLimits.MAX_VISIBLE_PAGE_COUNT)
            // Dopełnij do NOWEJ liczby aktywnych stron (parytet z setVisiblePageCountAction).
            // Dopełnianie do bieżącego storedPageCount nie rozszerzało metadanych przy
            // zwiększaniu (np. 2→4), więc normalizeGroup cofał activePageCount z powrotem.
            val (names, langs) = padPageMetadata(g.pageNames, g.pageLanguages, clamped)
            g.copy(activePageCount = clamped, pageNames = names, pageLanguages = langs, updatedAt = now)
        }
    }

fun setStudyFilter(groups: List<FlashcardGroup>, groupId: String, filter: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(studyFilter = normalizeStudyFilter(filter), updatedAt = now) else it }

fun setCardOrder(groups: List<FlashcardGroup>, groupId: String, order: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(cardOrder = normalizeCardOrder(order), updatedAt = now) else it }

fun setActiveStudyMode(groups: List<FlashcardGroup>, groupId: String, modeId: String, now: Long): List<FlashcardGroup> =
    groups.map { if (it.id == groupId) it.copy(activeModeId = modeId, updatedAt = now) else it }

fun movePage(groups: List<FlashcardGroup>, groupId: String, fromIndex: Int, toIndex: Int, now: Long): List<FlashcardGroup> =
    groups.map { g ->
        if (g.id != groupId) g
        else {
            val names = g.pageNames.toMutableList()
            val langs = g.pageLanguages.toMutableList()
            if (fromIndex in names.indices && toIndex in names.indices) {
                val tempName = names[fromIndex]
                names[fromIndex] = names[toIndex]
                names[toIndex] = tempName
                val tempLang = langs[fromIndex]
                langs[fromIndex] = langs[toIndex]
                langs[toIndex] = tempLang
            }
            val newCards = g.cards.map { c ->
                val pages = c.pages.toMutableList()
                if (fromIndex in pages.indices && toIndex in pages.indices) {
                    val temp = pages[fromIndex]
                    pages[fromIndex] = pages[toIndex]
                    pages[toIndex] = temp
                }
                c.copy(pages = pages)
            }
            g.copy(pageNames = names, pageLanguages = langs, cards = newCards, updatedAt = now)
        }
    }
