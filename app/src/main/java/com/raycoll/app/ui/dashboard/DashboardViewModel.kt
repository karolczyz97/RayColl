package com.raycoll.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val studyModeRepository: StudyModeRepository,
) : ViewModel() {

    val decks: StateFlow<List<FlashcardGroup>> = deckRepository.activeDecks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val modes: StateFlow<List<StudyMode>> = studyModeRepository.liveModes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            deckRepository.seedIfEmpty()
            studyModeRepository.seedIfEmpty()
        }
    }

    fun setActiveMode(deckId: String, modeId: String) {
        viewModelScope.launch { deckRepository.setActiveStudyMode(deckId, modeId) }
    }

    fun archiveDeck(deckId: String) {
        viewModelScope.launch { deckRepository.archiveDeck(deckId) }
    }
}
