package com.raycoll.app.ui.decksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.FlashcardGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckSettingsViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {
    private val _deck = MutableStateFlow<FlashcardGroup?>(null)
    val deck: StateFlow<FlashcardGroup?> = _deck

    fun loadDeck(deckId: String) { viewModelScope.launch { _deck.value = deckRepository.getDeck(deckId) } }
    fun updateName(deckId: String, name: String) { viewModelScope.launch {
        val deck = _deck.value ?: return@launch
        deckRepository.updateDeck(deck.copy(name = name))
        _deck.value = deck.copy(name = name)
    }}
    fun setVisiblePageCount(deckId: String, count: Int) { viewModelScope.launch { deckRepository.setVisiblePageCount(deckId, count) } }
    fun setStudyFilter(deckId: String, filter: String) { viewModelScope.launch { deckRepository.setStudyFilter(deckId, filter) } }
    fun setCardOrder(deckId: String, order: String) { viewModelScope.launch { deckRepository.setCardOrder(deckId, order) } }
    fun archive(deckId: String) { viewModelScope.launch { deckRepository.archiveDeck(deckId) } }
    fun delete(deckId: String) { viewModelScope.launch { deckRepository.deleteDeck(deckId) } }
}
