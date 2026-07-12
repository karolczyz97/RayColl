package com.raycoll.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.selectors.computeStreak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DeckSummary(
    val id: String,
    val name: String,
    val stats: CardStats,
)

data class StatsUiState(
    val streak: Int = 0,
    val heatmap: Map<String, Int> = emptyMap(),
    val totalCards: Int = 0,
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val reviewCount: Int = 0,
    val masteredCount: Int = 0,
    val decks: List<DeckSummary> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    deckRepository: DeckRepository,
) : ViewModel() {
    val stats: StateFlow<StatsUiState> = combine(
        deckRepository.activeDecks(),
        deckRepository.activityHeatmapFlow(),
    ) { decks, heatmap ->
        val allCards = decks.flatMap { it.cards }
        val cardStats = computeCardStats(allCards)
        StatsUiState(
            streak = computeStreak(heatmap, System.currentTimeMillis()),
            heatmap = heatmap,
            totalCards = cardStats.total,
            newCount = cardStats.newCount,
            learningCount = cardStats.learning,
            reviewCount = cardStats.review,
            masteredCount = cardStats.mastered,
            decks = decks.map { DeckSummary(it.id, it.name, computeCardStats(it.cards)) },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, StatsUiState())
}
