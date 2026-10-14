package com.raycoll.app.ui.decksettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.resolveStudyMode
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import com.raycoll.app.ui.navigation.DeckSettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

sealed interface DeckSettingsUiState {
    data object Loading : DeckSettingsUiState

    data object DeckMissing : DeckSettingsUiState

    data class Ready(val deck: FlashcardGroup, val modes: List<StudyMode>) : DeckSettingsUiState {
        /** Tryb, którym talia się uczy — zaznaczony na liście także wtedy, gdy przypisany usunięto. */
        val activeMode: StudyMode get() = resolveStudyMode(modes, deck.activeModeId)
    }
}

sealed interface DeckSettingsEvent {
    /** Talia zarchiwizowana albo usunięta — ekran się zamyka. */
    data object Closed : DeckSettingsEvent

    data object DeleteFailed : DeckSettingsEvent

    /** Zmiana ustawienia się nie zapisała. */
    data object SaveFailed : DeckSettingsEvent
}

@HiltViewModel
class DeckSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val modeRepository: StudyModeRepository
) : ViewModel() {
    val deckId: String = savedStateHandle.toRoute<DeckSettingsRoute>().deckId

    // po archiwizacji/usunięciu z tego ekranu zostaje ostatni widok, dopóki ekran się zamyka
    private val closing = MutableStateFlow(false)
    private var lastReady: DeckSettingsUiState.Ready? = null

    val uiState: StateFlow<DeckSettingsUiState> = combine(
        deckRepository.deck(deckId),
        modeRepository.liveModes(),
        closing
    ) { deck, modes, closing ->
        when {
            deck != null -> DeckSettingsUiState.Ready(deck, modes).also { lastReady = it }

            closing -> lastReady ?: DeckSettingsUiState.DeckMissing

            // usunięta (np. na innym urządzeniu) albo nieistniejąca talia
            else -> DeckSettingsUiState.DeckMissing
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckSettingsUiState.Loading)

    private val eventSink = UiEvents<DeckSettingsEvent>()
    val events: Flow<DeckSettingsEvent> = eventSink.flow

    /** Pusta nazwa nie jest zapisywana — talia zostaje przy poprzedniej. */
    fun rename(name: String) {
        if (name.isBlank()) return
        launch { deckRepository.renameDeck(deckId, name) }
    }

    fun setPageName(pageIndex: Int, name: String) = launch { deckRepository.setPageName(deckId, pageIndex, name) }

    fun setPageLanguage(pageIndex: Int, language: String) = launch {
        deckRepository.setPageLanguage(deckId, pageIndex, language)
    }

    fun swapPages(from: Int, to: Int) = launch { deckRepository.swapPages(deckId, from, to) }

    fun setActivePageCount(count: Int, defaultName: (Int) -> String = { "Page ${it + 1}" }) = launch {
        deckRepository.setActivePageCount(deckId, count, defaultName)
    }

    fun setStudyFilter(filter: StudyFilter) = launch { deckRepository.setStudyFilter(deckId, filter) }

    fun setCardOrder(order: CardOrder) = launch { deckRepository.setCardOrder(deckId, order) }

    fun setActiveMode(modeId: String) = launch { deckRepository.setActiveStudyMode(deckId, modeId) }

    fun resetMode(modeId: String) = launch { modeRepository.resetMode(modeId) }

    fun deleteMode(modeId: String) = launch { modeRepository.deleteMode(modeId) }

    fun archive() {
        closing.value = true
        viewModelScope.launchSaving(onFailure = {
            closing.value = false
            eventSink.send(DeckSettingsEvent.SaveFailed)
        }) {
            deckRepository.archiveDeck(deckId)
            eventSink.send(DeckSettingsEvent.Closed)
        }
    }

    fun delete() {
        closing.value = true
        viewModelScope.launchSaving(onFailure = {
            closing.value = false
            eventSink.send(DeckSettingsEvent.DeleteFailed)
        }) {
            deckRepository.deleteDeck(deckId)
            eventSink.send(DeckSettingsEvent.Closed)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(DeckSettingsEvent.SaveFailed) }, block = block)
    }
}
