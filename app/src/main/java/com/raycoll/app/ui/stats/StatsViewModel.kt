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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val streak: Int = 0,
    val totalCards: Int = 0,
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val reviewCount: Int = 0,
    val masteredCount: Int = 0,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    deckRepository: DeckRepository,
) : ViewModel() {
    val stats: StateFlow<StatsUiState> = deckRepository.activeDecks().map { decks ->
        val allCards = decks.flatMap { it.cards }
        val cardStats = computeCardStats(allCards)
        StatsUiState(
            streak = computeStreak(emptyMap(), System.currentTimeMillis()),
            totalCards = cardStats.total,
            newCount = cardStats.newCount,
            learningCount = cardStats.learning,
            reviewCount = cardStats.review,
            masteredCount = cardStats.mastered,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, StatsUiState())
}
