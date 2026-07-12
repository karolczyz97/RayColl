package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup

fun getVisiblePages(card: Flashcard, group: FlashcardGroup): List<String> {
    val pages = card.pages
    val count = group.activePageCount
    val result = pages.take(count).toMutableList()
    while (result.size < count) result.add("")
    return result
}

fun getVisiblePageNames(group: FlashcardGroup): List<String> =
    group.pageNames.take(group.activePageCount)

fun getAllPages(card: Flashcard, group: FlashcardGroup): List<String> = card.pages
