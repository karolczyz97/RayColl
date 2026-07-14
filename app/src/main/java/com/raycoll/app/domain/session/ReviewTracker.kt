package com.raycoll.app.domain.session

class ReviewTracker {
    private val reviewedCardIds = mutableSetOf<String>()

    fun newAttempt() {
        reviewedCardIds.clear()
    }

    fun isReviewed(cardId: String): Boolean = cardId in reviewedCardIds

    fun markReviewed(cardId: String) {
        reviewedCardIds.add(cardId)
    }
}
