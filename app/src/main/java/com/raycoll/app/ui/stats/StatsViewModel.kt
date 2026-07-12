package com.raycoll.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.LocalDates
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.selectors.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val heatmap: Map<String, Int> = emptyMap(),
    val streak: Int = 0,
    val totalCards: Int = 0,
    val activeDays: Int = 0,
    val globalStats: CardStatsData = CardStatsData(),
    val deckProgress: List<Pair<String, Int>> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(private val repo: DeckRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState
    init {
        viewModelScope.launch {
            repo.storeData.collect { data ->
                val active = selectActiveGroups(data.groups)
                _uiState.value = StatsUiState(
                    heatmap = data.activityHeatmap,
                    streak = computeStreak(data.activityHeatmap, LocalDates.today()),
                    totalCards = getTotalCardsCount(active),
                    activeDays = getActiveDaysCount(data.activityHeatmap),
                    globalStats = getGlobalStats(active),
                    deckProgress = active.map { it.name to getGroupProgress(it.cards) },
                )
            }
        }
    }
}
