package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.srs.getCardCategory
import com.raycoll.app.domain.srs.SrsCardCategory

data class SessionProgress(
    val total: Int, val completed: Int, val learning: Int, val review: Int, val mastered: Int,
)

fun computeSessionProgress(cards: List<Flashcard>, reviewedCardIds: Set<String>): SessionProgress {
    val live = cards.filter { it.deletedAt == null }
    val completed = live.count { it.id in reviewedCardIds }
    var learning = 0; var review = 0; var mastered = 0
    for (c in live) {
        when (getCardCategory(c.srsState)) {
            SrsCardCategory.New, SrsCardCategory.Learning -> learning++
            SrsCardCategory.Review -> review++
            SrsCardCategory.Mastered -> mastered++
        }
    }
    return SessionProgress(live.size, completed, learning, review, mastered)
}
