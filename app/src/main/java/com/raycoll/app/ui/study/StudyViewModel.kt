package com.raycoll.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.LocalDates
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.selectors.getDueCards
import com.raycoll.app.domain.session.*
import com.raycoll.app.domain.steps.expandModeSteps
import com.raycoll.app.playback.StudySessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyUiState(
    val sessionState: StudySessionState = StudySessionState(),
    val group: FlashcardGroup? = null, val currentCard: Flashcard? = null,
    val dueCount: Int = 0, val noDueCards: Boolean = false, val sessionFinished: Boolean = false,
)

@HiltViewModel
class StudyViewModel @Inject constructor(private val repo: DeckRepository, private val holder: StudySessionHolder) : ViewModel() {
    private val _uiState = MutableStateFlow(StudyUiState()); val uiState: StateFlow<StudyUiState> = _uiState
    private var cards: List<Flashcard> = emptyList(); private var mode: StudyMode? = null

    fun load(groupId: String) {
        viewModelScope.launch {
            val data = repo.load()
            val group = data.groups.find { it.id == groupId } ?: run { _uiState.value = StudyUiState(noDueCards = true); return@launch }
            mode = data.studyModes.find { it.id == group.activeModeId }
            cards = getDueCards(group.cards, group.studyFilter, group.cardOrder, System.currentTimeMillis())
            if (cards.isEmpty()) { _uiState.value = StudyUiState(group = group, noDueCards = true); return@launch }
            holder.create(group, cards); val engine = holder.engine!!
            launch(Dispatchers.Main) { engine.state.collect { s -> _uiState.value = StudyUiState(s, group, cards.getOrNull(s.currentCardIndex), cards.size, sessionFinished = s.status == SessionStatus.Finished) } }
            engine.start(mode ?: StudyMode("default", "Default"), expandModeSteps(mode?.steps ?: emptyList()))
        }
    }

    fun handleCardPress() { holder.engine?.handleCardPress() }
    fun handleHoldStart() { holder.engine?.handleHoldStart() }
    fun rate(rating: Int) { holder.engine?.rate(rating) }
    fun restartSession() { val g = _uiState.value.group ?: return; cards = getDueCards(g.cards, g.studyFilter, g.cardOrder, System.currentTimeMillis()); holder.engine?.restart(cards) }
    fun restartFailed() { holder.engine?.restartFailed() }

    override fun onCleared() {
        super.onCleared()
        val e = holder.engine ?: return
        val revs = e.reviews.toList()
        if (revs.isNotEmpty()) kotlinx.coroutines.runBlocking { repo.reviewBatch(revs, LocalDates.today()) }
        holder.release()
    }
}
