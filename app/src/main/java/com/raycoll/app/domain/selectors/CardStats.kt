package com.raycoll.app.domain.selectors

import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.domain.srs.getCardCategory

data class CardStats(
    val total: Int = 0,
    val newCount: Int = 0,
    val learning: Int = 0,
    val review: Int = 0,
    val mastered: Int = 0
)

fun computeCardStats(cards: List<Flashcard>): CardStats {
    val byCategory = cards.live().groupingBy { getCardCategory(it.srsState) }.eachCount()
    return CardStats(
        total = byCategory.values.sum(),
        newCount = byCategory[SrsCardCategory.NEW] ?: 0,
        learning = byCategory[SrsCardCategory.LEARNING] ?: 0,
        review = byCategory[SrsCardCategory.REVIEW] ?: 0,
        mastered = byCategory[SrsCardCategory.MASTERED] ?: 0
    )
}

/**
 * Seria kolejnych dni z ocenami, licząc od dziś (albo od wczoraj, jeśli dziś jeszcze
 * nic nie oceniono). Dzień startowy z wstrzykniętego [now], nie z zegara systemu.
 */
fun computeStreak(heatmap: Map<String, Int>, now: Long): Int {
    val today = LocalDates.localDate(now)
    val start = if ((heatmap[LocalDates.dayKey(today)] ?: 0) > 0) today else today.minusDays(1)
    var streak = 0
    var day = start
    while ((heatmap[LocalDates.dayKey(day)] ?: 0) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}
