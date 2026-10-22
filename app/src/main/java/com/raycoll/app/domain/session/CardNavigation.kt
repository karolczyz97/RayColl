package com.raycoll.app.domain.session

/** Identyfikuje konkretny gest: spóźnione zakończenie nie może zmienić innej karty ani nowej sesji. */
data class CardNavigation(val sessionId: Long, val sourceCardId: String, val sequence: Long)

sealed interface CardDestination {
    data class Card(val id: String) : CardDestination
    data object Summary : CardDestination
}
