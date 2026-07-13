package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.domain.srs.getCardCategory
import com.raycoll.app.core.LocalDates

data class CardStats(
    val total: Int = 0,
    val newCount: Int = 0,
    val learning: Int = 0,
    val review: Int = 0,
    val mastered: Int = 0,
)

fun computeCardStats(cards: List<Flashcard>): CardStats {
    val liveCards = filterLiveCards(cards)
    var newCount = 0; var learning = 0; var review = 0; var mastered = 0
    for (card in liveCards) {
        when (getCardCategory(card.srsState)) {
            SrsCardCategory.NEW -> newCount++
            SrsCardCategory.LEARNING -> learning++
            SrsCardCategory.REVIEW -> review++
            SrsCardCategory.MASTERED -> mastered++
        }
    }
    return CardStats(total = liveCards.size, newCount = newCount, learning = learning, review = review, mastered = mastered)
}

fun computeStreak(heatmap: Map<String, Int>, now: Long): Int {
    if (heatmap.isEmpty()) return 0
    var streak = 0
    // Data startowa MUSI pochodzić z wstrzykniętego `now` (nie z systemowego
    // LocalDate.now()), inaczej hasToday i iteracja mierzą dwie różne "dziś".
    var date = java.time.LocalDate.ofInstant(
        java.time.Instant.ofEpochMilli(now),
        java.time.ZoneId.systemDefault(),
    )
    val todayKey = LocalDates.dayKey(now)
    val hasToday = heatmap[todayKey] != null && (heatmap[todayKey] ?: 0) > 0
    if (!hasToday) {
        date = date.minusDays(1)
    }
    for (i in 0 until StudyConstants.STREAK_MAX_DAYS) {
        val key = date.minusDays(i.toLong()).toString()
        val count = heatmap[key]
        if (count != null && count > 0) streak++
        else break
    }
    return streak
}
