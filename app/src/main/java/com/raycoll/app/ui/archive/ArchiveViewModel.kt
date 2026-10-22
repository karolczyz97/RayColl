package com.raycoll.app.ui.archive

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Clock
import com.raycoll.app.core.minuteTicks
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.selectors.archiveDaysLeft
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ArchivedDeck(val id: String, val name: String, val stats: CardStats, val daysLeft: Int) {
    val cardCount: Int get() = stats.total
}

sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState

    data class Ready(val decks: List<ArchivedDeck>) : ArchiveUiState
}

sealed interface ArchiveEvent {
    data object SaveFailed : ArchiveEvent
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<ArchiveUiState> = combine(deckRepository.archivedDecks(), clock.minuteTicks()) {
            decks,
            now
        ->
        ArchiveUiState.Ready(
            decks.map { deck ->
                ArchivedDeck(
                    id = deck.id,
                    name = deck.name,
                    stats = computeCardStats(deck.cards),
                    daysLeft = archiveDaysLeft(deck, now) ?: 0
                )
            }
        )
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState.Loading)

    private val eventSink = UiEvents<ArchiveEvent>()

    /** Nie udało się zapisać przywrócenia lub usunięcia talii. */
    val events: Flow<ArchiveEvent> = eventSink.flow

    fun restore(deckId: String) {
        viewModelScope.launchSaving(onFailure = {
            eventSink.send(ArchiveEvent.SaveFailed)
        }) { deckRepository.restoreDeck(deckId) }
    }

    fun delete(deckId: String) {
        viewModelScope.launchSaving(onFailure = {
            eventSink.send(ArchiveEvent.SaveFailed)
        }) { deckRepository.deleteDeck(deckId) }
    }
}
