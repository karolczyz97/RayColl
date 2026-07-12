package com.raycoll.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.Flashcard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {
    private var currentDeckId: String = ""
    private val _cards = MutableStateFlow<List<Flashcard>>(emptyList())
    val cards: StateFlow<List<Flashcard>> = _cards

    fun loadDeck(deckId: String) {
        currentDeckId = deckId
        viewModelScope.launch {
            deckRepository.cardsForDeck(deckId).collect { _cards.value = it }
        }
    }

    fun addCard(deckId: String) { viewModelScope.launch { deckRepository.addCard(deckId, listOf("", "")) } }
    fun updateCard(deckId: String, card: Flashcard) { viewModelScope.launch { deckRepository.updateCard(deckId, card) } }
    fun deleteCard(deckId: String, cardId: String) { viewModelScope.launch { deckRepository.deleteCard(deckId, cardId) } }
}
