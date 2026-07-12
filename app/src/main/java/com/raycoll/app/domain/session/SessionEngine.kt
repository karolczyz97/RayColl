package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.selectors.getDueCards
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionEngine(
    private val group: FlashcardGroup,
    private val allCards: List<Flashcard>,
    private val deps: SessionDeps,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StudySessionState())
    val state: StateFlow<StudySessionState> = _state

    private val reviewTracker = mutableSetOf<String>()
    private var cards: List<Flashcard> = allCards
    private var steps: List<AtomicStep> = emptyList()
    private var baseSrsStates: Map<String, SrsState> = allCards.associate { it.id to it.srsState }
    private var stepJob: Job? = null
    private var lastCardAdvanceTime: Long = 0L
    val reviews = mutableListOf<ReviewEntry>()

    init {
        dispatch(SessionAction.SetHolding(false))
    }

    fun start(mode: StudyMode, expandedSteps: List<AtomicStep>) {
        this.steps = expandedSteps
        cards = getDueCards(allCards, group.studyFilter, group.cardOrder, System.currentTimeMillis())
        baseSrsStates = cards.associate { it.id to it.srsState }
        reviewTracker.clear(); reviews.clear()
        if (cards.isEmpty()) { _state.value = _state.value.copy(status = SessionStatus.Finished); return }
        _state.value = StudySessionState()
        lastCardAdvanceTime = System.currentTimeMillis()
        stepJob = scope.launch { runSteps(buildContext()) }
    }

    fun handleCardPress() {
        val s = _state.value
        when {
            s.interactionGate.kind == "tap_to_reveal" -> dispatch(SessionAction.CompleteInteractionGate)
            s.status == SessionStatus.Speaking -> { stepJob?.cancel(); scope.launch { dispatch(SessionAction.EndSpeaking); runSteps(buildContext()) } }
            s.status == SessionStatus.Listening -> { stepJob?.cancel(); dispatch(SessionAction.SetLastAnswerResult(LastAnswerResult(AnswerStatus.Skipped))); scope.launch { runSteps(buildContext()) } }
        }
    }

    fun handleHoldStart() {
        val card = cards.getOrNull(_state.value.currentCardIndex) ?: return
        val nextHidden = (0 until group.activePageCount).firstOrNull { it !in _state.value.revealedPages }
        if (nextHidden != null) dispatch(SessionAction.PeekSet(nextHidden))
        dispatch(SessionAction.SetHolding(true))
    }

    fun handleHoldEnd(waitMs: Long = 0L) {
        dispatch(SessionAction.SetHolding(false))
        val peek = _state.value.peekedPageIndex
        if (peek != null && waitMs < 500) dispatch(SessionAction.RevealNextPageInGate(peek))
        dispatch(SessionAction.PeekClear)
    }

    fun rate(rating: Int) {
        val s = _state.value
        val card = cards.getOrNull(s.currentCardIndex) ?: return
        deps.haptic(rating)

        if (card.id in reviewTracker) {
            val base = baseSrsStates[card.id] ?: card.srsState
            reviews.add(ReviewEntry(group.id, card.id, rating, true, base))
        } else {
            reviews.add(ReviewEntry(group.id, card.id, rating, false, baseSrsStates[card.id] ?: card.srsState))
            reviewTracker.add(card.id)
        }

        if (s.currentCardIndex + 1 >= cards.size) {
            dispatch(SessionAction.FinishSession)
            return
        }

        dispatch(SessionAction.AdvanceCard(s.currentCardIndex + 1))
        lastCardAdvanceTime = System.currentTimeMillis()
        stepJob?.cancel()
        stepJob = scope.launch { runSteps(buildContext()) }
    }

    fun goToPreviousCard() {
        val now = System.currentTimeMillis()
        if (now - lastCardAdvanceTime <= 3000 && _state.value.currentCardIndex > 0) {
            stepJob?.cancel()
            dispatch(SessionAction.AdvanceCard(_state.value.currentCardIndex - 1))
            stepJob = scope.launch { runSteps(buildContext()) }
        } else {
            stepJob?.cancel()
            dispatch(SessionAction.AdvanceCard(_state.value.currentCardIndex))
            _state.value = _state.value.copy(currentStepIndex = 0)
            stepJob = scope.launch { runSteps(buildContext()) }
        }
    }

    fun restart(newCards: List<Flashcard>) {
        cards = newCards
        baseSrsStates = cards.associate { it.id to it.srsState }
        reviewTracker.clear(); reviews.clear()
        if (cards.isEmpty()) { _state.value = _state.value.copy(status = SessionStatus.Finished); return }
        _state.value = StudySessionState()
        stepJob = scope.launch { runSteps(buildContext()) }
    }

    fun restartFailed() {
        val failed = cards.filter { it.id in _state.value.failedCardIds }
        if (failed.isEmpty()) return
        restart(failed)
    }

    fun end() { stepJob?.cancel(); dispatch(SessionAction.FinishSession) }
    fun stop() { stepJob?.cancel() }

    fun dispatch(action: SessionAction) { _state.value = sessionReducer(_state.value, action) }

    private suspend fun runSteps(ctx: SessionContext) {
        while (_state.value.status != SessionStatus.Finished) {
            if (_state.value.currentStepIndex >= steps.size) {
                val next = _state.value.currentCardIndex + 1
                if (next >= cards.size) { dispatch(SessionAction.FinishSession); break }
                dispatch(SessionAction.AdvanceCard(next)); continue
            }
            val pending = _state.value.pendingStepIndexToRun
            if (pending != null) { dispatch(SessionAction.ConsumePendingStepIndex); dispatch(SessionAction.SetCurrentStep(pending)) }
            val actions = StepExecutor.executeStep(_state.value, ctx)
            for (a in actions) {
                if (a is SessionAction.ShowRatings) { dispatch(a); return }
                dispatch(a)
            }
        }
    }

    private fun buildContext() = SessionContext(cards, steps, group, deps, baseSrsStates)
}
