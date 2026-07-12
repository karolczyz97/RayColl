package com.raycoll.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.selectors.getDueCards
import com.raycoll.app.domain.steps.CompoundExpander
import com.raycoll.app.playback.SessionDepsAdapter
import com.raycoll.app.playback.StudySessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyUiState(
    val status: SessionStatus = SessionStatus.IDLE,
    val currentCard: Flashcard? = null,
    val currentIndex: Int = 0,
    val totalCards: Int = 0,
    val revealedPages: List<Int> = listOf(0),
    val peekedPage: Int? = null,
    val showRatings: Boolean = false,
    val finished: Boolean = false,
    val endedEarly: Boolean = false,
    val errorMsg: String? = null,
    val partialSttText: String = "",
    val matchPercent: Int? = null,
    val matchThreshold: Int? = null,
    val answerStatus: String = "none",
    val audioPageIndex: Int? = null,
    val deckName: String = "",
    val failedCount: Int = 0,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val sessionHolder: StudySessionHolder,
    private val sessionDeps: SessionDepsAdapter,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var engine: SessionEngine? = null
    private var deck: FlashcardGroup? = null
    private var ttsRate: Float = 1.0f
    private var startedAt: Long = 0

    init {
        viewModelScope.launch {
            prefsRepository.preferences.collectLatest { prefs ->
                ttsRate = prefs.ttsRate
            }
        }
    }

    fun loadDeck(deckId: String) {
        viewModelScope.launch {
            val group = deckRepository.getDeck(deckId) ?: return@launch
            deck = group
            val now = clock.now()
            val due = getDueCards(group.cards, group.studyFilter, group.cardOrder, now)
            _uiState.value = _uiState.value.copy(
                totalCards = due.size,
                deckName = group.name,
                currentCard = due.firstOrNull(),
            )
            if (due.isEmpty()) {
                _uiState.value = _uiState.value.copy(finished = false)
                return@launch
            }
            startSession(due, group)
        }
    }

    private fun startSession(cards: List<Flashcard>, group: FlashcardGroup) {
        engine?.stop()
        val steps = CompoundExpander.expandModeSteps(
            group.activeModeId.let { id ->
                // Get mode steps from repository
                // For built-in modes, use seed
                when (id) {
                    "classic" -> com.raycoll.app.domain.srs.createSeedModes().find { it.id == "classic" }?.steps ?: emptyList()
                    "listen-speak" -> com.raycoll.app.domain.srs.createSeedModes().find { it.id == "listen-speak" }?.steps ?: emptyList()
                    else -> com.raycoll.app.domain.srs.createSeedModes().find { it.id == "classic" }?.steps ?: emptyList()
                }
            }
        )
        val eng = SessionEngine(
            group = group,
            steps = steps,
            deps = sessionDeps,
            clock = clock,
            ttsRate = ttsRate,
            scope = viewModelScope,
        )
        sessionHolder.create(eng, group.id, group.name)
        engine = eng
        startedAt = clock.now()

        viewModelScope.launch {
            eng.state.collectLatest { state ->
                _uiState.value = mapEngineState(state, group.name)
            }
        }
        eng.start(cards)
    }

    private fun mapEngineState(state: SessionState, deckName: String): StudyUiState {
        val card = state.cards.getOrNull(state.currentCardIndex)
        return StudyUiState(
            status = state.status,
            currentCard = card,
            currentIndex = state.currentCardIndex,
            totalCards = state.cards.size,
            revealedPages = state.revealedPages,
            peekedPage = state.peekedPageIndex,
            showRatings = state.status == SessionStatus.REVEALED,
            finished = state.status == SessionStatus.FINISHED,
            endedEarly = state.status == SessionStatus.FINISHED && clock.now() - startedAt < Long.MAX_VALUE,
            errorMsg = state.errorMsg,
            partialSttText = state.lastAnswerResult.text,
            matchPercent = state.lastAnswerResult.percent,
            matchThreshold = state.lastAnswerResult.threshold,
            answerStatus = state.lastAnswerResult.status.name.lowercase(),
            audioPageIndex = state.audioPageIndex,
            deckName = deckName,
            failedCount = state.failedCardIds.size,
        )
    }

    fun rate(rating: Int) {
        engine?.rate(rating)
    }

    fun skipToNextCard() {
        engine?.skipToNextCard()
    }

    fun goToPreviousCard() {
        engine?.goToPreviousCard()
    }

    fun handleCardPress() {
        engine?.handleCardPress()
    }

    fun setHolding(value: Boolean) {
        engine?.setHolding(value)
    }

    fun pause() {
        engine?.pause()
    }

    fun resume() {
        engine?.resume()
    }

    fun endSession() {
        engine?.end()
        sessionHolder.release()
        engine = null
    }

    override fun onCleared() {
        engine?.stop()
        sessionHolder.release()
        super.onCleared()
    }
}
