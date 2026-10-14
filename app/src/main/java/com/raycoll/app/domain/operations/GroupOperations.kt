package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.normalize.padPageMetadata

fun newGroup(
    name: String,
    pageLanguages: List<String>,
    pageNames: List<String>,
    cards: List<Flashcard>,
    now: Long
): FlashcardGroup = FlashcardGroup(
    id = Ids.newId(),
    name = name.trim(),
    cards = cards,
    pageLanguages = pageLanguages,
    pageNames = pageNames,
    activePageCount = PageLimits.clampActivePageCount(pageNames.size, PageLimits.MAX_VISIBLE_PAGE_COUNT),
    updatedAt = now
)

/** Zmiana liczby aktywnych stron; metadane dopełniane do NOWEJ liczby, żeby normalizacja jej nie cofnęła. */
fun FlashcardGroup.withActivePageCount(
    count: Int,
    now: Long,
    defaultName: (Int) -> String = { "Page ${it + 1}" }
): FlashcardGroup {
    val clamped = PageLimits.clampActivePageCount(count, PageLimits.MAX_VISIBLE_PAGE_COUNT)
    val (names, languages) = padPageMetadata(pageNames, pageLanguages, clamped, defaultName)
    return copy(activePageCount = clamped, pageNames = names, pageLanguages = languages, updatedAt = now)
}

/** Zamienia miejscami dwie strony: nazwę, język i treść w każdej karcie. */
fun FlashcardGroup.withPagesSwapped(from: Int, to: Int, now: Long): FlashcardGroup {
    if (from == to || from !in pageNames.indices || to !in pageNames.indices) return this
    val languages =
        pageLanguages + List((pageNames.size - pageLanguages.size).coerceAtLeast(0)) { DEFAULT_PAGE_LANGUAGE }
    return copy(
        pageNames = pageNames.swapped(from, to),
        pageLanguages = languages.swapped(from, to),
        cards = cards.map { card ->
            val pages = card.pages + List((maxOf(from, to) + 1 - card.pages.size).coerceAtLeast(0)) { "" }
            card.copy(pages = pages.swapped(from, to))
        },
        updatedAt = now
    )
}

fun FlashcardGroup.isArchiveExpired(now: Long): Boolean {
    val archivedAt = archivedAt ?: return false
    return deletedAt == null && now - archivedAt >= ArchiveConstants.RETENTION_MS
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    it[a] = this[b]
    it[b] = this[a]
}
