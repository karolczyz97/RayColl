package com.raycoll.app.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.domain.selectors.browseFilter
import com.raycoll.app.domain.selectors.computeCardStats
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import com.raycoll.app.ui.navigation.BrowseRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import kotlinx.coroutines.flow.stateIn

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
}

@HiltViewModel
class BrowseViewModel internal constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    /** Gdzie liczy się filtr listy; testy podstawiają wstrzymywany wątek. */
    filterContext: CoroutineContext
) : ViewModel() {
    @Inject constructor(
        savedStateHandle: SavedStateHandle,
        deckRepository: DeckRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(savedStateHandle, deckRepository, defaultDispatcher as CoroutineContext)

    val deckId: String = savedStateHandle.toRoute<BrowseRoute>().deckId

    private val eventSink = UiEvents<BrowseEvent>()
    val events: Flow<BrowseEvent> = eventSink.flow

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

    fun addCard(pages: List<String>) = write {
        deckRepository.addCard(deckId, pages)
        eventSink.send(BrowseEvent.CardAdded)
    }

    /** [visiblePages] — nowe widoczne strony; ukryte strony i postęp nauki karty zostają. */
    fun updateCard(cardId: String, visiblePages: List<String>) = write {
        if (deckRepository.updateCardPages(cardId, visiblePages)) {
            eventSink.send(BrowseEvent.Saved)
        } else {
            eventSink.send(BrowseEvent.CardMissing)
        }
    }

    fun deleteCard(cardId: String) = write { deckRepository.deleteCard(cardId) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(BrowseEvent.SaveFailed) }, block = block)
    }
}
