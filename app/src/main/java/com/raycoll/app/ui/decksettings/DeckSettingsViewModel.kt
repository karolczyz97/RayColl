package com.raycoll.app.ui.decksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckSettingsViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val modeRepository: StudyModeRepository,
) : ViewModel() {
    private val _deck = MutableStateFlow<FlashcardGroup?>(null)
    val deck: StateFlow<FlashcardGroup?> = _deck

    val modes: StateFlow<List<StudyMode>> = modeRepository.liveModes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setActiveMode(deckId: String, modeId: String) { viewModelScope.launch {
        deckRepository.setActiveStudyMode(deckId, modeId)
        _deck.value = _deck.value?.copy(activeModeId = modeId)
    }}
    fun deleteMode(modeId: String) { viewModelScope.launch { modeRepository.deleteMode(modeId) } }
    fun resetMode(modeId: String) { viewModelScope.launch { modeRepository.resetMode(modeId) } }

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

    fun updatePageName(deckId: String, pageIndex: Int, name: String) { viewModelScope.launch {
        val deck = _deck.value ?: return@launch
        val names = deck.pageNames.toMutableList()
        while (names.size <= pageIndex) names.add("")
        names[pageIndex] = name
        deckRepository.updateDeck(deck.copy(pageNames = names))
        _deck.value = deck.copy(pageNames = names)
    }}

    fun setPageLanguage(deckId: String, pageIndex: Int, lang: String) { viewModelScope.launch {
        val deck = _deck.value ?: return@launch
        val langs = deck.pageLanguages.toMutableList()
        while (langs.size <= pageIndex) langs.add("en-US")
        langs[pageIndex] = lang
        deckRepository.updateDeck(deck.copy(pageLanguages = langs))
        _deck.value = deck.copy(pageLanguages = langs)
    }}

    /** Zamiana stron miejscami: nazwy + języki + zawartość kart (transakcyjnie w repo). */
    fun movePage(deckId: String, from: Int, to: Int) { viewModelScope.launch {
        deckRepository.movePage(deckId, from, to)
        _deck.value = deckRepository.getDeck(deckId)
    }}

    /** Usunięcie z await — błąd zapisu zostawia użytkownika na ekranie z komunikatem. */
    fun delete(deckId: String, onDone: () -> Unit, onError: () -> Unit) { viewModelScope.launch {
        try {
            deckRepository.deleteDeck(deckId)
            onDone()
        } catch (_: Exception) {
            onError()
        }
    }}
}
