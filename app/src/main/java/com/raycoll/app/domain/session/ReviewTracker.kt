package com.raycoll.app.domain.session

class ReviewTracker {
    private val reviewedKeys = mutableSetOf<String>()
    private var attemptNumber = 0

    fun newAttempt() {
        attemptNumber++
        reviewedKeys.clear()
    }

    fun isReviewed(cardId: String): Boolean = reviewedKeys.contains("$attemptNumber:$cardId")

    fun markReviewed(cardId: String) {
        reviewedKeys.add("$attemptNumber:$cardId")
    }
}
