package com.raycoll.app.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Clock
import com.raycoll.app.core.LocalDates
import com.raycoll.app.core.minuteTicks
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.selectors.computeStreak
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
data class DeckStats(val id: String, val name: String, val stats: CardStats)

sealed interface StatsUiState {
    data object Loading : StatsUiState

    data class Ready(
        val today: LocalDate,
        val streak: Int,
        val heatmap: Map<String, Int>,
        val allCards: CardStats,
        val decks: List<DeckStats>
    ) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(deckRepository: DeckRepository, clock: Clock) : ViewModel() {
    val uiState: StateFlow<StatsUiState> = combine(
        deckRepository.activeDecks(),
        deckRepository.activityHeatmap(),
        clock.minuteTicks().flowOn(Dispatchers.Main.immediate)
    ) {
            decks,
            heatmap,
            now
        ->
        StatsUiState.Ready(
            today = LocalDates.localDate(now),
            streak = computeStreak(heatmap, now),
            heatmap = heatmap,
            allCards = computeCardStats(decks.flatMap { it.cards }),
            decks = decks.map { DeckStats(it.id, it.name, computeCardStats(it.cards)) }
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)
}
