package com.raycoll.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.selectors.*
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.theme.SrsColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val groups: List<FlashcardGroup> = emptyList(),
    val dueCards: Map<String, Int> = emptyMap(),
    val progress: Map<String, Int> = emptyMap(),
    val stats: Map<String, CardStatsData> = emptyMap(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: DeckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.init()
            repo.storeData.collect { data ->
                val active = selectActiveGroups(data.groups)
                val due = active.associate { g ->
                    g.id to getDueCards(g.cards, g.studyFilter, g.cardOrder, System.currentTimeMillis()).size
                }
                val progress = active.associate { g ->
                    g.id to getGroupProgress(g.cards)
                }
                val stats = active.associate { g ->
                    g.id to computeCardStats(g.cards)
                }
                _uiState.value = DashboardUiState(active, due, progress, stats)
            }
        }
    }

    fun addGroup(name: String, languages: List<String>, pageNames: List<String>) {
        viewModelScope.launch { repo.addGroup(name, languages, pageNames) }
    }
}
