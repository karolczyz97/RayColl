package com.raycoll.app.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Clock
import com.raycoll.app.core.minuteTicks
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.resolveStudyMode
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.selectors.countDueCards
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
data class DeckSummary(
    val id: String,
    val name: String,
    /** Tryb, którym talia się uczy (przypisany albo domyślny, gdy przypisany usunięto). */
    val activeMode: StudyMode,
    val stats: CardStats,
    val dueCount: Int
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(val decks: List<DeckSummary>, val modes: List<StudyMode>) : DashboardUiState
}

sealed interface DashboardEvent {
    data object SaveFailed : DashboardEvent
    data class Archived(val deckId: String) : DashboardEvent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    studyModeRepository: StudyModeRepository,
    private val clock: Clock,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        deckRepository.activeDecks(),
        studyModeRepository.liveModes(),
        clock.minuteTicks()
    ) { decks, modes, now ->
        val summaries = decks.map { deck ->
            DeckSummary(
                id = deck.id,
                name = deck.name,
                activeMode = resolveStudyMode(modes, deck.activeModeId),
                stats = computeCardStats(deck.cards),
                dueCount = countDueCards(deck.cards, deck.studyFilter, now)
            )
        }
        DashboardUiState.Ready(summaries, modes)
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    private val eventSink = UiEvents<DashboardEvent>()

    /** Zmiana z pulpitu (tryb, archiwizacja) się nie zapisała. */
    val events: Flow<DashboardEvent> = eventSink.flow

    fun setActiveMode(deckId: String, modeId: String) = save { deckRepository.setActiveStudyMode(deckId, modeId) }

    fun archiveDeck(deckId: String) = save {
        deckRepository.archiveDeck(deckId)
        eventSink.send(DashboardEvent.Archived(deckId))
    }

    fun restoreDeck(deckId: String) = save { deckRepository.restoreDeck(deckId) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(DashboardEvent.SaveFailed) }, block = block)
    }
}
