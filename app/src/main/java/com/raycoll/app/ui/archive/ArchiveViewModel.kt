package com.raycoll.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.FlashcardGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {
    val archivedDecks: StateFlow<List<FlashcardGroup>> = deckRepository.archivedDecks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun restore(deckId: String) { viewModelScope.launch { deckRepository.restoreDeck(deckId) } }
}
