package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.steps.CompoundExpander
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionEngine(
    private val group: FlashcardGroup,
    private val steps: List<ModeStep>,
    private val deps: SessionDeps,
    private val clock: Clock,
    private val ttsRate: Float,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SessionState())
    val state = _state.asStateFlow()

    private val reviewTracker = ReviewTracker()
    private var expandedSteps: List<ModeStep> = emptyList()
    private var cardJobs = mutableMapOf<Int, Job>()
    private var lastExecutedCardIndex = -1
    private var holding = MutableStateFlow(false)
    private val inBackground = MutableStateFlow(false)
    private var cardStartedAt: Long = 0L

    init {
        expandedSteps = CompoundExpander.expandModeSteps(steps).filter { step ->
            val pageIndex = com.raycoll.app.domain.steps.StepRegistry.getStepPageIndex(step)
            pageIndex == null || pageIndex < group.activePageCount
        }
    }

    fun start(cards: List<Flashcard>) {
        reviewTracker.newAttempt()
        _state.value = sessionReducer(_state.value, SessionAction.SetCards(cards))
        _state.value = sessionReducer(_state.value, SessionAction.StartSession)
        if (cards.isEmpty()) {
            _state.value = sessionReducer(_state.value, SessionAction.FinishSession)
            return
        }
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    private fun runCurrentCard() {
        val cardIndex = _state.value.currentCardIndex
        if (cardIndex < 0 || cardIndex >= _state.value.cards.size) return
        // ta sama karta może być wznowiona tylko gdy czeka pending step (np. po gate)
        if (cardIndex == lastExecutedCardIndex && _state.value.pendingStepIndexToRun == null) return
        lastExecutedCardIndex = cardIndex
        cardStartedAt = clock.now()

        cardJobs[cardIndex]?.cancel()
        cardJobs[cardIndex] = scope.launch {
            val card = _state.value.cards[cardIndex]
            var stepIndex = 0
            while (stepIndex < expandedSteps.size) {
                if (_state.value.status == SessionStatus.FINISHED) break
                val pending = _state.value.pendingStepIndexToRun
                if (pending != null) {
                    _state.value = sessionReducer(_state.value, SessionAction.ConsumePendingStepIndex)
                    stepIndex = pending
                    continue
                }
                val step = expandedSteps[stepIndex]
                if (inBackground.value && substituteInBackground(step, card)) {
                    stepIndex++
                    continue
                }
                val executor = StepExecutor(deps, _state, group, ttsRate)
                executor.executeStep(step, card, stepIndex)
                if (_state.value.status == SessionStatus.REVEALED) return@launch
                if (_state.value.interactionGate.kind != InteractionGateKind.NONE) return@launch
                if (step is com.raycoll.app.domain.model.NextCardStep) {
                    while (holding.value) { kotlinx.coroutines.delay(50) }
                    advanceToNextCard()
                    return@launch
                }
                stepIndex++
            }
        }
    }

    fun rate(rating: Int) {
        if (_state.value.cardReviewed) return
        val card = _state.value.cards.getOrNull(_state.value.currentCardIndex) ?: return
        deps.haptic(rating)
        deps.onCardReviewed(group.id, card.id, rating, null)
        _state.value = sessionReducer(_state.value, SessionAction.SetCardReviewed)
        scope.launch { advanceToNextCard() }
    }

    private suspend fun advanceToNextCard() {
        val nextIndex = _state.value.currentCardIndex + 1
        if (nextIndex >= _state.value.cards.size) {
            _state.value = sessionReducer(_state.value, SessionAction.FinishSession)
            return
        }
        _state.value = sessionReducer(_state.value, SessionAction.AdvanceCard(nextIndex))
        runCurrentCard()
    }

    fun skipToNextCard() {
        cardJobs[_state.value.currentCardIndex]?.cancel()
        scope.launch { advanceToNextCard() }
    }

    fun goToPreviousCard() {
        val s = _state.value
        cardJobs[s.currentCardIndex]?.cancel()
        // <=3 s od startu karty: cofnij do poprzedniej; później: restart bieżącej od kroku 0
        val targetIndex = if (clock.now() - cardStartedAt <= 3000 && s.currentCardIndex > 0) {
            s.currentCardIndex - 1
        } else {
            s.currentCardIndex
        }
        _state.value = sessionReducer(_state.value, SessionAction.AdvanceCard(targetIndex))
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    /**
     * Substytucje kroków interaktywnych w tle (sekcja 6.6 planu).
     * Zwraca true gdy krok został obsłużony zastępczo.
     */
    private suspend fun substituteInBackground(step: ModeStep, card: Flashcard): Boolean {
        val activePages = card.pages.take(group.activePageCount)
        return when (step) {
            is com.raycoll.app.domain.model.WaitForTapStep -> {
                kotlinx.coroutines.delay(BackgroundSubstitution.calculateBgWaitMs(activePages))
                true
            }
            is com.raycoll.app.domain.model.WaitForTapToRevealNextStep -> {
                kotlinx.coroutines.delay(BackgroundSubstitution.calculateBgWaitMs(activePages))
                val hidden = (0 until group.activePageCount).filter { it !in _state.value.revealedPages }
                hidden.firstOrNull()?.let {
                    _state.value = sessionReducer(_state.value, SessionAction.RevealNextPageInGate(it))
                }
                true
            }
            is com.raycoll.app.domain.model.WaitForTapToRevealStep -> {
                kotlinx.coroutines.delay(BackgroundSubstitution.calculateBgWaitMs(activePages))
                val hidden = (0 until group.activePageCount).filter { it !in _state.value.revealedPages }
                hidden.forEach {
                    _state.value = sessionReducer(_state.value, SessionAction.RevealNextPageInGate(it))
                }
                true
            }
            else -> false
        }
    }

    fun setBackground(value: Boolean) { inBackground.value = value }

    fun handleCardPress() {
        val s = _state.value
        when (s.status) {
            SessionStatus.SPEAKING, SessionStatus.IDLE -> {
                if (s.interactionGate.kind == InteractionGateKind.TAP_TO_REVEAL) {
                    handleGateTap()
                } else {
                    cardJobs[s.currentCardIndex]?.cancel()
                    scope.launch { advanceToNextCard() }
                }
            }
            SessionStatus.LISTENING -> {
                // Skip STT -> result 'skipped'
                _state.value = sessionReducer(_state.value, SessionAction.SetLastAnswerResult(
                    LastAnswerResult(status = AnswerStatus.SKIPPED, text = "")
                ))
            }
            SessionStatus.REVEALED, SessionStatus.FINISHED, SessionStatus.ERROR -> {}
        }
    }

    private fun handleGateTap() {
        val gate = _state.value.interactionGate
        if (gate.kind != InteractionGateKind.TAP_TO_REVEAL) return
        val hiddenPages = (0 until group.activePageCount).filter { it !in _state.value.revealedPages }
        when (gate.revealMode) {
            TapRevealMode.SINGLE -> {
                if (hiddenPages.isNotEmpty()) {
                    _state.value = sessionReducer(_state.value, SessionAction.RevealNextPageInGate(hiddenPages.first()))
                }
                _state.value = sessionReducer(_state.value, SessionAction.CompleteInteractionGate)
                runCurrentCard()
            }
            TapRevealMode.REMAINING -> {
                if (hiddenPages.isNotEmpty()) {
                    _state.value = sessionReducer(_state.value, SessionAction.RevealNextPageInGate(hiddenPages.first()))
                    if (hiddenPages.size <= 1) {
                        _state.value = sessionReducer(_state.value, SessionAction.CompleteInteractionGate)
                        runCurrentCard()
                    }
                } else {
                    _state.value = sessionReducer(_state.value, SessionAction.CompleteInteractionGate)
                    runCurrentCard()
                }
            }
            TapRevealMode.NONE -> {
                _state.value = sessionReducer(_state.value, SessionAction.CompleteInteractionGate)
                runCurrentCard()
            }
            null -> {
                _state.value = sessionReducer(_state.value, SessionAction.CompleteInteractionGate)
                runCurrentCard()
            }
        }
    }

    fun setHolding(value: Boolean) { holding.value = value }

    fun pause() {
        holding.value = false
        cardJobs[_state.value.currentCardIndex]?.cancel()
    }

    fun resume() {
        // bieżąca karta wciąż leci → nic nie rób (idempotentne, wołane też przez autostart playera)
        if (cardJobs[_state.value.currentCardIndex]?.isActive == true) return
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    fun stop() {
        cardJobs.values.forEach { it.cancel() }
        cardJobs.clear()
        _state.value = sessionReducer(_state.value, SessionAction.FinishSession)
    }

    fun end() {
        stop()
    }
}
