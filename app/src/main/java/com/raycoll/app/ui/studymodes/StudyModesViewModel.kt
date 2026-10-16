package com.raycoll.app.ui.studymodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface StudyModesUiState {
    data object Loading : StudyModesUiState

    data class Ready(val modes: List<StudyMode>) : StudyModesUiState
}

sealed interface StudyModesEvent {
    data object SaveFailed : StudyModesEvent
}

@HiltViewModel
class StudyModesViewModel @Inject constructor(private val studyModeRepository: StudyModeRepository) : ViewModel() {

    val uiState: StateFlow<StudyModesUiState> = studyModeRepository.liveModes()
        .map(StudyModesUiState::Ready)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyModesUiState.Loading)

    private val eventSink = UiEvents<StudyModesEvent>()

    /** Przywrócenie albo usunięcie trybu się nie zapisało. */
    val events: Flow<StudyModesEvent> = eventSink.flow

    fun resetMode(modeId: String) = save { studyModeRepository.resetMode(modeId) }

    fun deleteMode(modeId: String) = save { studyModeRepository.deleteMode(modeId) }

    fun restoreMode(modeId: String) = save { studyModeRepository.restoreMode(modeId) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(StudyModesEvent.SaveFailed) }, block = block)
    }
}
