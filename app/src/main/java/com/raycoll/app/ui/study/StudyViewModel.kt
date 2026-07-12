package com.raycoll.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.selectors.getDueCards
import com.raycoll.app.domain.session.BackgroundSubstitution
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.steps.CompoundExpander
import com.raycoll.app.playback.SessionDepsAdapter
import com.raycoll.app.playback.StudySessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyUiState(
    val status: SessionStatus = SessionStatus.IDLE,
    val currentCard: Flashcard? = null,
    val currentIndex: Int = 0,
    val totalCards: Int = 0,
    val sessionCards: List<Flashcard> = emptyList(),
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
    val backgroundEligible: Boolean = false,
    val needsMicPermission: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val modeRepository: StudyModeRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val sessionHolder: StudySessionHolder,
    private val sessionDeps: SessionDepsAdapter,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var engine: SessionEngine? = null
    private var deck: FlashcardGroup? = null
    private var deckId: String = ""
    private var ttsRate: Float = 1.0f
    private var backgroundEligible = false
    private var pausedByLifecycle = false
    private var endedEarlyFlag = false
    private var needsMic = false
    private var stateJob: Job? = null

    init {
        viewModelScope.launch {
            prefsRepository.preferences.collectLatest { prefs ->
                ttsRate = prefs.ttsRate
            }
        }
    }

    fun loadDeck(deckId: String) {
        this.deckId = deckId
        viewModelScope.launch {
            val group = deckRepository.getDeck(deckId) ?: return@launch
            deck = group
            val now = clock.now()
            val due = getDueCards(group.cards, group.studyFilter, group.cardOrder, now)
            _uiState.value = _uiState.value.copy(
                totalCards = due.size,
                deckName = group.name,
                currentCard = due.firstOrNull(),
                loaded = true,
            )
            if (due.isEmpty()) return@launch
            startSession(due, group)
        }
    }

    private suspend fun startSession(cards: List<Flashcard>, group: FlashcardGroup) {
        engine?.stop()
        endedEarlyFlag = false
        // aktywny tryb z bazy (własne i edytowane wbudowane); fallback: seed classic
        val steps = modeRepository.getModes()
            .find { it.id == group.activeModeId && it.deletedAt == null }
            ?.steps
            ?: com.raycoll.app.domain.srs.createSeedModes().find { it.id == "classic" }?.steps
            ?: emptyList()
        val expanded = CompoundExpander.expandModeSteps(steps)
        needsMic = expanded.any { it is ListenAndCheckStep }
        val prefs = prefsRepository.preferences.first()
        backgroundEligible = prefs.backgroundPlayback &&
            BackgroundSubstitution.isModeHandsFreeCapable(expanded)
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

        stateJob?.cancel()
        stateJob = viewModelScope.launch {
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
            sessionCards = state.cards,
            revealedPages = state.revealedPages,
            peekedPage = state.peekedPageIndex,
            showRatings = state.status == SessionStatus.REVEALED,
            finished = state.status == SessionStatus.FINISHED,
            endedEarly = state.status == SessionStatus.FINISHED && endedEarlyFlag,
            errorMsg = state.errorMsg,
            partialSttText = state.lastAnswerResult.text,
            matchPercent = state.lastAnswerResult.percent,
            matchThreshold = state.lastAnswerResult.threshold,
            answerStatus = state.lastAnswerResult.status.name.lowercase(),
            audioPageIndex = state.audioPageIndex,
            deckName = deckName,
            failedCount = state.failedCardIds.size,
            backgroundEligible = backgroundEligible,
            needsMicPermission = needsMic,
            loaded = true,
        )
    }

    /** Apka schodzi w tło: hands-free + pref ON → tryb tła (substytucje); inaczej zwykła pauza. */
    fun onAppBackground() {
        val eng = engine ?: return
        if (_uiState.value.finished) return
        if (backgroundEligible) {
            eng.setBackground(true)
        } else {
            pausedByLifecycle = true
            eng.pause()
        }
    }

    fun onAppForeground() {
        val eng = engine ?: return
        eng.setBackground(false)
        if (pausedByLifecycle) {
            pausedByLifecycle = false
            eng.resume()
        }
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

    fun pressDown() {
        engine?.pressDown()
    }

    fun pressUp() {
        engine?.pressUp()
    }

    fun clearError() {
        engine?.clearError()
    }

    /** "Od nowa": świeże wersje wszystkich kart sesji z repozytorium. */
    fun restartSession() {
        val eng = engine ?: return
        val ids = eng.state.value.cards.map { it.id }
        viewModelScope.launch {
            endedEarlyFlag = false
            eng.restart(freshCardsByIds(ids))
        }
    }

    /** "Powtórz oblane": świeże wersje kart z listy failed. */
    fun restartFailed() {
        val eng = engine ?: return
        val failedIds = eng.state.value.let { s -> s.cards.map { it.id }.filter { it in s.failedCardIds } }
        viewModelScope.launch {
            endedEarlyFlag = false
            eng.restartFailed(freshCardsByIds(failedIds))
        }
    }

    private suspend fun freshCardsByIds(ids: List<String>): List<Flashcard> {
        val fresh = deckRepository.getDeck(deckId) ?: return emptyList()
        val byId = fresh.cards.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** Potwierdzenie dialogu przerwania: sesja kończy się podsumowaniem endedEarly. */
    fun endSessionEarly() {
        endedEarlyFlag = true
        engine?.end()
    }

    /** Wyjście z ekranu — zwolnienie sesji i serwisu. */
    fun exitSession() {
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
