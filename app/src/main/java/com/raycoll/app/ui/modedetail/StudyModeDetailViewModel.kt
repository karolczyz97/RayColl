package com.raycoll.app.ui.modedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.StudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudyModeDetailViewModel @Inject constructor(private val repo: DeckRepository) : ViewModel() {
    private val _mode = MutableStateFlow<StudyMode?>(null)
    val mode: StateFlow<StudyMode?> = _mode

    fun load(modeId: String) {
        viewModelScope.launch {
            repo.storeData.collect { data ->
                _mode.value = data.studyModes.find { it.id == modeId }
            }
        }
    }
}
