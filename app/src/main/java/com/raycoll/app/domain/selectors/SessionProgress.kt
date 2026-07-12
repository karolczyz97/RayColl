package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.domain.srs.getCardCategory

data class SessionCardInfo(val card: Flashcard, val category: SrsCardCategory, val isPast: Boolean, val isCurrent: Boolean)

fun getSessionProgress(cards: List<Flashcard>, currentIndex: Int): List<SessionCardInfo> =
    cards.mapIndexed { index, card ->
        SessionCardInfo(
            card = card,
            category = getCardCategory(card.srsState),
            isPast = index < currentIndex,
            isCurrent = index == currentIndex,
        )
    }
