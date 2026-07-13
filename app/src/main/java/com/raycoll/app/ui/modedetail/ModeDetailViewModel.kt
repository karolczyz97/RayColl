package com.raycoll.app.ui.modedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.steps.ModeIssue
import com.raycoll.app.domain.steps.ModeStepValidator
import com.raycoll.app.domain.steps.ModeTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Maks. liczba stron karty wg modelu danych (2..20) — walidacja indeksów stron w edytorze. */
private const val MAX_PAGES = 20

enum class ModeTemplate { CLASSIC, AUDIO, BLANK }

data class ModeDetailUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = false,
    val templateChosen: Boolean = false,
    val name: String = "",
    val steps: List<ModeStep> = emptyList(),
    val isBuiltIn: Boolean = false,
    val issues: List<ModeIssue> = emptyList(),
) {
    // Tylko problemy blokujące (name/steps/page-out-of-range) wyłączają zapis —
    // NoTerminalStep/AutoRateWithoutStt to ostrzeżenia (parytet z validateModeSteps.ts).
    val saveEnabled: Boolean get() = loaded && issues.none { it.blocking }
}

@HiltViewModel
class ModeDetailViewModel @Inject constructor(
    private val modeRepository: StudyModeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModeDetailUiState())
    val uiState: StateFlow<ModeDetailUiState> = _uiState.asStateFlow()

    private var existingMode: StudyMode? = null
    private var initialized = false

    fun init(modeId: String?) {
        if (initialized) return
        initialized = true
        if (modeId == null) {
            _uiState.value = ModeDetailUiState(loaded = true, isNew = true, templateChosen = false)
            return
        }
        viewModelScope.launch {
            val mode = modeRepository.getModes().find { it.id == modeId }
            if (mode == null) {
                _uiState.value = ModeDetailUiState(loaded = true, isNew = true, templateChosen = false)
            } else {
                existingMode = mode
                _uiState.value = validated(
                    ModeDetailUiState(
                        loaded = true, isNew = false, templateChosen = true,
                        name = mode.name, steps = mode.steps, isBuiltIn = mode.isBuiltIn,
                    )
                )
            }
        }
    }

    fun chooseTemplate(template: ModeTemplate) {
        val steps = when (template) {
            ModeTemplate.CLASSIC -> ModeTemplates.classicTemplate()
            ModeTemplate.AUDIO -> ModeTemplates.audioTemplate()
            ModeTemplate.BLANK -> ModeTemplates.blankTemplate()
        }
        _uiState.value = validated(_uiState.value.copy(templateChosen = true, steps = steps))
    }

    fun setName(name: String) {
        _uiState.value = validated(_uiState.value.copy(name = name))
    }

    fun updateStep(index: Int, step: ModeStep) {
        val steps = _uiState.value.steps.toMutableList()
        if (index !in steps.indices) return
        steps[index] = step
        _uiState.value = validated(_uiState.value.copy(steps = steps))
    }

    fun addStep(step: ModeStep) {
        _uiState.value = validated(_uiState.value.copy(steps = _uiState.value.steps + step))
    }

    fun removeStep(index: Int) {
        val steps = _uiState.value.steps.toMutableList()
        if (index !in steps.indices) return
        steps.removeAt(index)
        _uiState.value = validated(_uiState.value.copy(steps = steps))
    }

    fun moveStep(index: Int, delta: Int) {
        val steps = _uiState.value.steps.toMutableList()
        val target = index + delta
        if (index !in steps.indices || target !in steps.indices) return
        val tmp = steps[index]; steps[index] = steps[target]; steps[target] = tmp
        _uiState.value = validated(_uiState.value.copy(steps = steps))
    }

    fun save(onDone: () -> Unit) {
        val state = _uiState.value
        if (!state.saveEnabled) return
        viewModelScope.launch {
            val existing = existingMode
            if (existing == null) {
                modeRepository.addMode(state.name, state.steps)
            } else {
                modeRepository.updateMode(existing.copy(name = state.name, steps = state.steps))
            }
            onDone()
        }
    }

    private fun validated(state: ModeDetailUiState): ModeDetailUiState {
        val probe = StudyMode(id = "probe", name = state.name, steps = state.steps)
        return state.copy(issues = ModeStepValidator.validate(probe, MAX_PAGES))
    }
}
