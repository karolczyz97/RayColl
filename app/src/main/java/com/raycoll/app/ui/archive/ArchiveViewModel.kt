package com.raycoll.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.selectors.selectArchivedGroups
import com.raycoll.app.domain.selectors.archiveDaysLeft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchiveUiState(val groups: List<FlashcardGroup> = emptyList())

@HiltViewModel
class ArchiveViewModel @Inject constructor(private val repo: DeckRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState
    init {
        viewModelScope.launch {
            repo.storeData.collect { data ->
                _uiState.value = ArchiveUiState(selectArchivedGroups(data.groups))
            }
        }
    }
    fun restore(groupId: String) { viewModelScope.launch { repo.restoreGroup(groupId) } }
    fun delete(groupId: String) { viewModelScope.launch { repo.deleteGroup(groupId) } }
}
