package com.raycoll.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {
    private var currentDeckId: String = ""
    private var collectJob: Job? = null

    private val _cards = MutableStateFlow<List<Flashcard>>(emptyList())
    val cards: StateFlow<List<Flashcard>> = _cards

    private val _deck = MutableStateFlow<FlashcardGroup?>(null)
    val deck: StateFlow<FlashcardGroup?> = _deck

    /** Id świeżo dodanej karty — ekran od razu otwiera jej edycję. */
    private val _editRequestCardId = MutableStateFlow<String?>(null)
    val editRequestCardId: StateFlow<String?> = _editRequestCardId

    fun loadDeck(deckId: String) {
        if (deckId == currentDeckId && collectJob?.isActive == true) return
        currentDeckId = deckId
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            deckRepository.cardsForDeck(deckId).collect {
                _cards.value = it
                _deck.value = deckRepository.getDeck(deckId)
            }
        }
    }

    fun addCard(deckId: String) {
        viewModelScope.launch {
            val pageCount = _deck.value?.activePageCount ?: 2
            val id = deckRepository.addCard(deckId, List(pageCount) { "" })
            _editRequestCardId.value = id
        }
    }

    fun consumeEditRequest() { _editRequestCardId.value = null }

    fun updateCard(deckId: String, card: Flashcard) { viewModelScope.launch { deckRepository.updateCard(deckId, card) } }
    fun deleteCard(deckId: String, cardId: String) { viewModelScope.launch { deckRepository.deleteCard(deckId, cardId) } }
}
