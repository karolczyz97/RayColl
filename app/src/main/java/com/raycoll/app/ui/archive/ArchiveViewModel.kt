package com.raycoll.app.ui.archive

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Clock
import com.raycoll.app.core.minuteTicks
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.selectors.archiveDaysLeft
import com.raycoll.app.domain.selectors.live
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ArchivedDeck(val id: String, val name: String, val cardCount: Int, val daysLeft: Int)

sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState

    data class Ready(val decks: List<ArchivedDeck>) : ArchiveUiState
}

sealed interface ArchiveEvent {
    data object SaveFailed : ArchiveEvent
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(private val deckRepository: DeckRepository, private val clock: Clock) :
    ViewModel() {
    val uiState: StateFlow<ArchiveUiState> = combine(deckRepository.archivedDecks(), clock.minuteTicks()) {
            decks,
            now
        ->
        ArchiveUiState.Ready(
            decks.map { deck ->
                ArchivedDeck(
                    id = deck.id,
                    name = deck.name,
                    cardCount = deck.cards.live().size,
                    daysLeft = archiveDaysLeft(deck, now) ?: 0
                )
            }
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState.Loading)

    private val eventSink = UiEvents<ArchiveEvent>()

    /** Przywrócenie talii się nie zapisało. */
    val events: Flow<ArchiveEvent> = eventSink.flow

    fun restore(deckId: String) {
        viewModelScope.launchSaving(onFailure = {
            eventSink.send(ArchiveEvent.SaveFailed)
        }) { deckRepository.restoreDeck(deckId) }
    }
}
