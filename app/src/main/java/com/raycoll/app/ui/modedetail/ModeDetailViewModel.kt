package com.raycoll.app.ui.modedetail

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Ids
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.isLive
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.domain.seed.seedModeFor
import com.raycoll.app.domain.steps.ModeIssue
import com.raycoll.app.domain.steps.ModeStepValidator
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.components.launchSaving
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Krok na liście edytora; stałe [key] pozwala animować przestawianie. */
@Immutable
data class EditableStep(val step: ModeStep, val key: String = Ids.newId())

sealed interface ModeDetailUiState {
    data object Loading : ModeDetailUiState

    /** Nowy tryb: najpierw wybór szablonu. */
    data object ChoosingTemplate : ModeDetailUiState

    data class Editing(
        val isNew: Boolean,
        val name: String,
        val steps: List<EditableStep>,
        /** Wbudowany tryb, z którego pochodzi edytowany (można go przywrócić). */
        val builtIn: BuiltInMode?,
        val issues: List<ModeIssue>,
        val saving: Boolean = false,
        /** Nazwa i kroki w chwili otwarcia edytora (albo wyboru szablonu) — punkt odniesienia dla [dirty]. */
        val baselineName: String = name,
        val baselineSteps: List<ModeStep> = steps.map { it.step }
    ) : ModeDetailUiState {
        val canSave: Boolean get() = !saving && issues.none { it.blocking }

        /** Czy użytkownik zmienił coś względem stanu z otwarcia ekranu (wyjście zgubiłoby te zmiany). */
        val dirty: Boolean get() = name != baselineName || steps.map { it.step } != baselineSteps

        /** Przywracanie ma sens tylko, gdy edytowany stan różni się od wersji fabrycznej. */
        val canReset: Boolean get() = !saving && seedModeFor(builtIn?.id)
            ?.let { seed -> seed.name != name || seed.steps != steps.map { it.step } } == true
    }
}

sealed interface ModeDetailEvent {
    data object Closed : ModeDetailEvent

    data object SaveFailed : ModeDetailEvent
}

/** Niezapisany stan edytora; przeżywa śmierć procesu (w [SavedStateHandle]). */
@Serializable
private data class ModeDraft(
    val isNew: Boolean,
    val name: String,
    val steps: List<ModeStep>,
    val builtInId: String?,
    val baselineName: String,
    val baselineSteps: List<ModeStep>
)

private const val DRAFT_KEY = "modeDraft"

/** [modeId] == null: nowy tryb. */
@HiltViewModel(assistedFactory = ModeDetailViewModel.Factory::class)
class ModeDetailViewModel @AssistedInject constructor(
    @Assisted private val modeId: String?,
    private val modeRepository: StudyModeRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(modeId: String?): ModeDetailViewModel
    }

    private var existingMode: StudyMode? = null

    private val restoredDraft: ModeDraft? = savedState.get<String>(DRAFT_KEY)?.let(::decodeDraft)

    /** Ostatnio usunięty krok (z miejscem na liście) — do cofnięcia. */
    private var lastRemoved: Pair<Int, EditableStep>? = null

    private val _uiState = MutableStateFlow<ModeDetailUiState>(ModeDetailUiState.Loading)
    val uiState: StateFlow<ModeDetailUiState> = _uiState.asStateFlow()

    val expertMode: StateFlow<Boolean> = preferencesRepository.preferences
        .map { it.expertMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val eventSink = UiEvents<ModeDetailEvent>()
    val events: Flow<ModeDetailEvent> = eventSink.flow

    init {
        viewModelScope.launch {
            val mode = modeId?.let { modeRepository.getMode(it) }?.takeIf { it.isLive }
            existingMode = mode
            // szkic zmian w trybie, który w międzyczasie usunięto, nie ma czego edytować
            val draft = restoredDraft?.takeIf { it.isNew || mode != null }
            _uiState.value = when {
                draft != null -> draft.toEditing()

                mode == null -> ModeDetailUiState.ChoosingTemplate

                else -> editing(
                    isNew = false,
                    name = mode.name,
                    steps = mode.steps.map(::EditableStep),
                    builtIn = BuiltInMode.fromId(mode.builtInSourceId)?.takeIf { mode.isBuiltIn }
                )
            }
            _uiState.collect { state ->
                savedState[DRAFT_KEY] = (state as? ModeDetailUiState.Editing)?.let { Json.encodeToString(it.toDraft()) }
            }
        }
    }

    fun chooseTemplate(template: ModeTemplate) {
        _uiState.value = editing(isNew = true, name = "", steps = template.steps.map(::EditableStep), builtIn = null)
    }

    fun setName(name: String) = updateEditing { it.copy(name = name) }

    fun setExpertMode(value: Boolean) {
        viewModelScope.launchSaving(onFailure = { eventSink.send(ModeDetailEvent.SaveFailed) }) {
            preferencesRepository.setExpertMode(value)
        }
    }

    fun updateStep(index: Int, step: ModeStep) = updateStepList { steps ->
        if (index in steps.indices) steps[index] = steps[index].copy(step = step)
    }

    fun addStep(step: ModeStep, afterKey: String? = null) = updateStepList { steps ->
        val after = steps.indexOfFirst { it.key == afterKey }
        steps.add(if (after >= 0) after + 1 else steps.size, EditableStep(step))
    }

    fun removeStep(index: Int) {
        val state = _uiState.value as? ModeDetailUiState.Editing ?: return
        val removed = state.steps.getOrNull(index) ?: return
        lastRemoved = index to removed
        updateStepList { if (index in it.indices) it.removeAt(index) }
    }

    /** Przywraca ostatnio usunięty krok na to samo miejsce (z tym samym kluczem, więc z animacją). */
    fun undoRemove() {
        val (index, step) = lastRemoved ?: return
        lastRemoved = null
        updateStepList { steps ->
            if (steps.none { it.key == step.key }) steps.add(index.coerceIn(0, steps.size), step)
        }
    }

    fun moveStep(from: Int, to: Int) = updateStepList { steps ->
        if (from in steps.indices && to in steps.indices) steps.add(to, steps.removeAt(from))
    }

    fun save() {
        val state = _uiState.value as? ModeDetailUiState.Editing ?: return
        if (!state.canSave) return
        val steps = state.steps.map { it.step }
        persist {
            val existing = existingMode
            when {
                existing == null -> {
                    val id = modeRepository.addMode(state.name, steps)
                    existingMode = StudyMode(id = id, name = state.name, steps = steps)
                }

                // Bez zmian w nazwie i krokach nie wykonujemy zbędnego zapisu.
                existing.name == state.name && existing.steps == steps -> Unit

                else -> {
                    val updated = existing.copy(name = state.name, steps = steps)
                    modeRepository.updateMode(updated)
                    existingMode = updated
                }
            }
        }
    }

    fun resetToBuiltIn() {
        val existing = existingMode ?: return
        persist { modeRepository.resetMode(existing.id) }
    }

    private fun persist(write: suspend () -> Unit) {
        val state = _uiState.value as? ModeDetailUiState.Editing ?: return
        if (state.saving) return
        _uiState.value = state.copy(saving = true)
        viewModelScope.launchSaving(onFailure = { eventSink.send(ModeDetailEvent.SaveFailed) }) {
            try {
                write()
                eventSink.send(ModeDetailEvent.Closed)
            } finally {
                updateEditing { it.copy(saving = false) }
            }
        }
    }

    private fun editing(isNew: Boolean, name: String, steps: List<EditableStep>, builtIn: BuiltInMode?) =
        ModeDetailUiState.Editing(isNew, name, steps, builtIn, issues = validate(name, steps))

    private fun updateEditing(transform: (ModeDetailUiState.Editing) -> ModeDetailUiState.Editing) =
        _uiState.update { state ->
            if (state !is ModeDetailUiState.Editing) return@update state
            val edited = transform(state)
            edited.copy(issues = validate(edited.name, edited.steps))
        }

    private fun updateStepList(change: (MutableList<EditableStep>) -> Unit) = updateEditing { state ->
        state.copy(steps = state.steps.toMutableList().apply(change))
    }

    private fun ModeDraft.toEditing() = ModeDetailUiState.Editing(
        isNew = isNew,
        name = name,
        steps = steps.map(::EditableStep),
        builtIn = BuiltInMode.fromId(builtInId),
        issues = ModeStepValidator.validate(name, steps),
        baselineName = baselineName,
        baselineSteps = baselineSteps
    )

    private fun ModeDetailUiState.Editing.toDraft() = ModeDraft(
        isNew = isNew,
        name = name,
        steps = steps.map { it.step },
        builtInId = builtIn?.id,
        baselineName = baselineName,
        baselineSteps = baselineSteps
    )

    private fun validate(name: String, steps: List<EditableStep>) = ModeStepValidator.validate(
        name,
        steps.map {
            it.step
        }
    )
}

/** Szkic w innym formacie (np. ze starszej wersji aplikacji) przepada — edytor startuje od trybu z bazy. */
private fun decodeDraft(json: String): ModeDraft? = try {
    Json.decodeFromString<ModeDraft>(json)
} catch (e: IllegalArgumentException) {
    // także SerializationException
    Log.w("ModeDetailViewModel", "Dropping unreadable mode draft", e)
    null
}
