package com.raycoll.app.ui.decksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.operations.resolveStudyMode
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data class Archived(val deckId: String) : DeckSettingsEvent

    data object DeleteFailed : DeckSettingsEvent

    /** Zmiana ustawienia się nie zapisała. */
    data object SaveFailed : DeckSettingsEvent

    /** null — nazwa talii; liczba — nazwa strony. Okno zamyka się dopiero po udanym zapisie. */
    data class NameSaved(val pageIndex: Int?) : DeckSettingsEvent
}

@HiltViewModel(assistedFactory = DeckSettingsViewModel.Factory::class)
class DeckSettingsViewModel @AssistedInject constructor(
    @Assisted val deckId: String,
    private val deckRepository: DeckRepository,
    private val modeRepository: StudyModeRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(deckId: String): DeckSettingsViewModel
    }

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
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckSettingsUiState.Loading)

    private val eventSink = UiEvents<DeckSettingsEvent>()
    val events: Flow<DeckSettingsEvent> = eventSink.flow
    private val _savingName = MutableStateFlow(false)
    val savingName: StateFlow<Boolean> = _savingName.asStateFlow()

    /** Pusta nazwa nie jest zapisywana — talia zostaje przy poprzedniej. */
    fun rename(name: String) {
        if (name.isBlank()) return
        saveName(null) { deckRepository.renameDeck(deckId, name) }
    }

    fun setPageName(pageIndex: Int, name: String) = saveName(pageIndex) {
        deckRepository.setPageName(deckId, pageIndex, name)
    }

    fun setPageLanguage(pageIndex: Int, language: String) = launch {
        deckRepository.setPageLanguage(deckId, pageIndex, language)
    }

    fun movePage(from: Int, to: Int) = launch {
        deckRepository.movePage(deckId, from, to)
    }

    fun setActivePageCount(count: Int, defaultName: (Int) -> String) = launch {
        deckRepository.setActivePageCount(deckId, count, defaultName)
    }

    fun setStudyFilter(filter: StudyFilter) = launch { deckRepository.setStudyFilter(deckId, filter) }

    fun setCardOrder(order: CardOrder) = launch { deckRepository.setCardOrder(deckId, order) }

    fun setActiveMode(modeId: String) = launch { deckRepository.setActiveStudyMode(deckId, modeId) }

    fun resetMode(modeId: String) = launch { modeRepository.resetMode(modeId) }

    fun deleteMode(modeId: String) = launch { modeRepository.deleteMode(modeId) }

    fun restoreMode(modeId: String) = launch { modeRepository.restoreMode(modeId) }

    fun archive() {
        if (closing.value) return
        closing.value = true
        viewModelScope.launchSaving(onFailure = {
            closing.value = false
            eventSink.send(DeckSettingsEvent.SaveFailed)
        }) {
            deckRepository.archiveDeck(deckId)
            eventSink.send(DeckSettingsEvent.Archived(deckId))
        }
    }

    fun delete() {
        if (closing.value) return
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

    private fun saveName(pageIndex: Int?, block: suspend () -> Unit) {
        if (_savingName.value) return
        _savingName.value = true
        launch {
            try {
                block()
                eventSink.send(DeckSettingsEvent.NameSaved(pageIndex))
            } finally {
                _savingName.value = false
            }
        }
    }
}
