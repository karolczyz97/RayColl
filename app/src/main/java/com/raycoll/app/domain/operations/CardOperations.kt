package com.raycoll.app.domain.operations

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.srs.calculateFsrs

fun newCard(pages: List<String>, now: Long): Flashcard =
    Flashcard(id = Ids.newId(), pages = pages, srsState = SrsState(), contentUpdatedAt = now)

/**
 * Ocena karty. [baseState] = stan sprzed pierwszej oceny w tej sesji: ponowna ocena
 * tej samej karty nadpisuje poprzednią zamiast liczyć FSRS od już zmienionego stanu.
 */
fun Flashcard.reviewed(rating: Int, now: Long, baseState: SrsState = srsState): Flashcard =
    copy(srsState = calculateFsrs(baseState, rating, now), srsUpdatedAt = now)
