package com.raycoll.app.ui.decksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckSettingsUiState(val group: FlashcardGroup? = null, val modes: List<StudyMode> = emptyList())

@HiltViewModel
class DeckSettingsViewModel @Inject constructor(private val repo: DeckRepository) : ViewModel() {
    private val _s = MutableStateFlow(DeckSettingsUiState()); val uiState: StateFlow<DeckSettingsUiState> = _s
    fun load(groupId: String) { viewModelScope.launch { repo.storeData.collect { _s.value = DeckSettingsUiState(it.groups.find { g -> g.id == groupId }, it.studyModes) } } }
    fun updateGroup(g: FlashcardGroup) { viewModelScope.launch { repo.updateGroup(g) } }
    fun setActiveMode(groupId: String, modeId: String) { val g = _s.value.group ?: return; updateGroup(g.copy(activeModeId = modeId)) }
    fun archive(groupId: String) { viewModelScope.launch { repo.archiveGroup(groupId) } }
    fun delete(groupId: String) { viewModelScope.launch { repo.deleteGroup(groupId) } }
}
