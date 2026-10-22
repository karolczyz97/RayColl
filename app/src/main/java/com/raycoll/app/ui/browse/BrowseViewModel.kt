package com.raycoll.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.selectors.browseFilter
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BrowseUiState {
    data object Loading : BrowseUiState

    /** Talii nie ma (np. usunięta na innym urządzeniu). */
    data object DeckMissing : BrowseUiState

    data class Ready(
        val deckName: String,
        val pageNames: List<String>,
        val activePageCount: Int,
        val cards: List<Flashcard>,
        /** Wszystkie karty talii, bez filtra — edytowana karta nie znika, gdy wypadnie z filtra. */
        val allCards: List<Flashcard>,
        val counts: CardStats,
        val category: SrsCardCategory?
    ) : BrowseUiState
}

sealed interface BrowseEvent {
    data object Saved : BrowseEvent
    data object CardAdded : BrowseEvent

    /** Edytowanej karty już nie ma (usunięta w międzyczasie) — nic nie zapisano. */
    data object CardMissing : BrowseEvent

    data object SaveFailed : BrowseEvent

    /** Karta usunięta — do cofnięcia z komunikatu. */
    data class CardDeleted(val cardId: String) : BrowseEvent
}

@HiltViewModel(assistedFactory = BrowseViewModel.Factory::class)
class BrowseViewModel internal constructor(
    private val deckId: String,
    private val deckRepository: DeckRepository,
    private val preferencesRepository: UserPreferencesRepository,
    /** Gdzie liczy się filtr listy; testy podstawiają wstrzymywany wątek. */
    filterContext: CoroutineContext
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted deckId: String,
        deckRepository: DeckRepository,
        preferencesRepository: UserPreferencesRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(deckId, deckRepository, preferencesRepository, defaultDispatcher as CoroutineContext)

    @AssistedFactory
    interface Factory {
        fun create(deckId: String): BrowseViewModel
    }

    private val eventSink = UiEvents<BrowseEvent>()
    val events: Flow<BrowseEvent> = eventSink.flow

    private val _savingCard = MutableStateFlow(false)
    val savingCard: StateFlow<Boolean> = _savingCard.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()
    private val category = MutableStateFlow<SrsCardCategory?>(null)

    val uiState: StateFlow<BrowseUiState> = combine(deckRepository.deck(deckId), search, category) {
            deck,
            query,
            selected
        ->
        if (deck == null) return@combine BrowseUiState.DeckMissing
        BrowseUiState.Ready(
            deckName = deck.name,
            pageNames = deck.pageNames,
            activePageCount = deck.activePageCount,
            cards = browseFilter(deck.cards, query, selected, deck.activePageCount),
            allCards = deck.cards,
            counts = computeCardStats(deck.cards),
            category = selected
        )
    }
        .flowOn(filterContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState.Loading)

    fun setSearch(query: String) {
        _search.value = query
    }

    fun setCategory(value: SrsCardCategory?) {
        category.value = value
    }

    fun addCard(pages: List<String>) = writeCard {
        deckRepository.addCard(deckId, pages)
        eventSink.send(BrowseEvent.CardAdded)
    }

    /** [visiblePages] — nowe widoczne strony; ukryte strony i postęp nauki karty zostają. */
    fun updateCard(cardId: String, visiblePages: List<String>) = writeCard {
        if (deckRepository.updateCardPages(cardId, visiblePages)) {
            eventSink.send(BrowseEvent.Saved)
        } else {
            eventSink.send(BrowseEvent.CardMissing)
        }
    }

    /** Ile razy jeszcze pokazać podpowiedź gestu usuwania (0, dopóki ustawienia się nie wczytają). */
    val swipeHintsLeft: StateFlow<Int> = preferencesRepository.preferences
        .map { it.swipeHintsLeft }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun swipeHintShown() {
        viewModelScope.launch { preferencesRepository.swipeHintShown() }
    }

    /** [swiped] — usunięcie przesunięciem: użytkownik zna gest, podpowiedź znika na zawsze. */
    fun deleteCard(cardId: String, swiped: Boolean = false) = write {
        if (swiped) preferencesRepository.swipeLearned()
        deckRepository.deleteCard(cardId)
        eventSink.send(BrowseEvent.CardDeleted(cardId))
    }

    fun restoreCard(cardId: String) = write { deckRepository.restoreCard(cardId) }

    private fun writeCard(block: suspend () -> Unit) {
        if (_savingCard.value) return
        _savingCard.value = true
        write {
            try {
                block()
            } finally {
                _savingCard.value = false
            }
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(BrowseEvent.SaveFailed) }, block = block)
    }
}
