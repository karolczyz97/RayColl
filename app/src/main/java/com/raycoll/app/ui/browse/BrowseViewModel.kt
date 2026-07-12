package com.raycoll.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.selectors.categorizeForBrowse
import com.raycoll.app.domain.selectors.searchCards
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val group: FlashcardGroup? = null,
    val allCards: List<Flashcard> = emptyList(),
    val newCount: Int = 0, val learningCount: Int = 0,
    val reviewCount: Int = 0, val masteredCount: Int = 0,
    val searchQuery: String = "",
    val progress: Int = 0,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repo: DeckRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState

    fun load(groupId: String) {
        viewModelScope.launch {
            repo.storeData.collect { data ->
                val g = data.groups.find { it.id == groupId } ?: return@collect
                val result = categorizeForBrowse(g.cards)
                _uiState.value = BrowseUiState(g, result.all, result.new.size, result.learning.size, result.review.size, result.mastered.size)
            }
        }
    }

    fun search(query: String) {
        val s = _uiState.value
        val g = s.group ?: return
        val filtered = searchCards(g.cards, query, g)
        _uiState.value = s.copy(searchQuery = query, allCards = filtered)
    }

    fun deleteCard(cardId: String) {
        val s = _uiState.value
        val g = s.group ?: return
        viewModelScope.launch {
            val updated = com.raycoll.app.domain.operations.deleteFlashcard(listOf(g), g.id, cardId, System.currentTimeMillis())
            repo.updateGroup(updated.first())
        }
    }

    fun saveCard(card: Flashcard) {
        val s = _uiState.value
        val g = s.group ?: return
        viewModelScope.launch { repo.updateGroup(g.copy(cards = g.cards.map { if (it.id == card.id) card else it })) }
    }
}
