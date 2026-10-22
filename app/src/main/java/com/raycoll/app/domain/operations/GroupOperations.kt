package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.core.moved
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.defaultPageName
import com.raycoll.app.domain.model.isLive
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
    defaultName: (Int) -> String = ::defaultPageName
): FlashcardGroup {
    val clamped = PageLimits.clampActivePageCount(count, PageLimits.MAX_VISIBLE_PAGE_COUNT)
    val (names, languages) = padPageMetadata(pageNames, pageLanguages, clamped, defaultName)
    return copy(activePageCount = clamped, pageNames = names, pageLanguages = languages, updatedAt = now)
}

/** Przenosi stronę [from] na miejsce [to] (nazwę, język i treść w każdej karcie); strony pomiędzy przesuwają się o jedno. */
fun FlashcardGroup.withPageMoved(from: Int, to: Int, now: Long): FlashcardGroup {
    if (from == to || from !in pageNames.indices || to !in pageNames.indices) return this
    val languages =
        pageLanguages + List((pageNames.size - pageLanguages.size).coerceAtLeast(0)) { DEFAULT_PAGE_LANGUAGE }
    return copy(
        pageNames = pageNames.moved(from, to),
        pageLanguages = languages.moved(from, to),
        cards = cards.map { card ->
            val pages = card.pages + List((maxOf(from, to) + 1 - card.pages.size).coerceAtLeast(0)) { "" }
            card.copy(pages = pages.moved(from, to))
        },
        updatedAt = now
    )
}

/** Nowa nazwa strony [index]; talia bez zmian, gdy takiej strony nie ma. */
fun FlashcardGroup.withPageName(index: Int, name: String): FlashcardGroup =
    copy(pageNames = pageNames.replacedAt(index, name.trim()))

/** Nowy język strony [index]; talia bez zmian, gdy takiej strony nie ma. */
fun FlashcardGroup.withPageLanguage(index: Int, language: String): FlashcardGroup =
    copy(pageLanguages = pageLanguages.replacedAt(index, language))

fun FlashcardGroup.isArchiveExpired(now: Long): Boolean {
    val archivedAt = archivedAt ?: return false
    return isLive && now - archivedAt >= ArchiveConstants.RETENTION_MS
}

private fun <T> List<T>.replacedAt(index: Int, value: T): List<T> =
    if (index in indices) toMutableList().also { it[index] = value } else this
