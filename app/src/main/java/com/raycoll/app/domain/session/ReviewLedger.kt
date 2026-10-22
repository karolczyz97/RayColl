package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState

/**
 * Oceny jednej próby sesji: stan SRS sprzed pierwszej oceny karty jest bazą dla ponownej
 * (nadpisującej) oceny; klucze = karty ocenione w tej próbie.
 */
internal class ReviewLedger {
    private val baseStates = mutableMapOf<String, SrsState>()

    val reviewedCount: Int get() = baseStates.size

    fun clear() = baseStates.clear()

    /** Baza wcześniejszej oceny tej karty; null = pierwsza ocena (stan karty zostaje zapamiętany jako baza). */
    fun previousBase(card: Flashcard): SrsState? {
        val base = baseStates[card.id]
        if (base == null) baseStates[card.id] = card.srsState
        return base
    }

    fun base(card: Flashcard): SrsState = baseStates.getValue(card.id)
}
