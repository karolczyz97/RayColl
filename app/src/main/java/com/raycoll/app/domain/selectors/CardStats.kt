package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.srs.getCardCategory

import com.raycoll.app.domain.srs.SrsCardCategory

data class CardStatsData(
    val total: Int = 0, val newCount: Int = 0,
    val learning: Int = 0, val review: Int = 0, val mastered: Int = 0,
)

fun computeCardStats(cards: List<Flashcard>): CardStatsData {
    var new = 0; var learning = 0; var review = 0; var mastered = 0
    for (card in cards) {
        when (getCardCategory(card.srsState)) {
            SrsCardCategory.New -> new++
            SrsCardCategory.Learning -> learning++
            SrsCardCategory.Review -> review++
            SrsCardCategory.Mastered -> mastered++
        }
    }
    return CardStatsData(cards.size, new, learning, review, mastered)
}

fun computeStreak(heatmap: Map<String, Int>, todayKey: String): Int {
    val today = java.time.LocalDate.parse(todayKey)
    var streak = 0
    val hasToday = (heatmap[todayKey] ?: 0) > 0
    if (hasToday) streak++
    for (i in 1 until 365) {
        val day = today.minusDays(i.toLong()).toString()
        if ((heatmap[day] ?: 0) > 0) streak++ else break
    }
    return streak
}

fun getTotalCardsCount(groups: List<FlashcardGroup>): Int = groups.sumOf { it.cards.size }
fun getActiveDaysCount(heatmap: Map<String, Int>): Int = heatmap.size

fun getTotalDueCardsCount(groups: List<FlashcardGroup>, now: Long): Int =
    groups.sumOf { getDueCards(it.cards, it.studyFilter, it.cardOrder, now).size }

fun getGlobalStats(groups: List<FlashcardGroup>): CardStatsData = computeCardStats(groups.flatMap { it.cards })
