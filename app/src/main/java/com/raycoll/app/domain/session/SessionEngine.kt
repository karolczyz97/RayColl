package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.steps.CompoundExpander
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SessionEngine(
    private val group: FlashcardGroup,
    private val steps: List<ModeStep>,
    private val deps: SessionDeps,
    private val clock: Clock,
    private val ttsRate: Float,
    private val scope: CoroutineScope,
) {
    companion object {
        const val ERROR_TTS = "tts_error"
        const val ERROR_TTS_MISSING_VOICE = "tts_missing_voice"
        const val ERROR_STT = "stt_error"
    }

    private val _state = MutableStateFlow(SessionState())
    val state = _state.asStateFlow()

    /** Callback dla serwisu tła: auto-pauza (błąd TTS w tle) → notyfikacja "wstrzymane". */
    var onBackgroundAutoPause: (() -> Unit)? = null

    private val reviewTracker = ReviewTracker()
    // snapshot SRS sprzed pierwszej oceny w tej próbie — baza dla nadpisującej ponownej oceny
    private val baseSrsStates = mutableMapOf<String, SrsState>()
    private var expandedSteps: List<ModeStep> = emptyList()
    private var cardJobs = mutableMapOf<Int, Job>()
    private var lastExecutedCardIndex = -1
    private val holding = MutableStateFlow(false)
    private val inBackground = MutableStateFlow(false)
    private var cardStartedAt: Long = 0L
    private var holdStartedAt: Long = 0L
    private var peekTimerJob: Job? = null
    private var skippableJob: Job? = null

    init {
        expandedSteps = CompoundExpander.expandModeSteps(steps).filter { step ->
            val pageIndex = com.raycoll.app.domain.steps.StepRegistry.getStepPageIndex(step)
            pageIndex == null || pageIndex < group.activePageCount
        }
    }

    fun start(cards: List<Flashcard>) {
        cardJobs.values.forEach { it.cancel() }
        cardJobs.clear()
        peekTimerJob?.cancel()
        holding.value = false
        reviewTracker.newAttempt()
        baseSrsStates.clear()
        _state.value = sessionReducer(_state.value, SessionAction.SetCards(cards))
        _state.value = sessionReducer(_state.value, SessionAction.StartSession)
        if (cards.isEmpty()) {
            _state.value = sessionReducer(_state.value, SessionAction.FinishSession)
            return
        }
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    /** "Od nowa": świeże wersje wszystkich kart sesji (dostarcza VM z repozytorium). */
    fun restart(freshCards: List<Flashcard>) = start(freshCards)

    /** "Powtórz oblane": świeże wersje kart z listy failed. */
    fun restartFailed(freshCards: List<Flashcard>) {
        if (freshCards.isNotEmpty()) start(freshCards)
    }

    private fun runCurrentCard() {
        val cardIndex = _state.value.currentCardIndex
        if (cardIndex < 0 || cardIndex >= _state.value.cards.size) return
        // ta sama karta może być wznowiona tylko gdy czeka pending step (np. po gate)
        if (cardIndex == lastExecutedCardIndex && _state.value.pendingStepIndexToRun == null) return
        lastExecutedCardIndex = cardIndex
        cardStartedAt = clock.now()

        cardJobs[cardIndex]?.cancel()
        // LAZY: referencja musi trafić do mapy ZANIM łańcuch ruszy (immediate dispatcher
        // wykonałby ciało przed przypisaniem i anulowanie trafiałoby w stary job)
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val card = _state.value.cards[cardIndex]
            val executor = StepExecutor(deps, _state, group, ttsRate, this@SessionEngine)
            var stepIndex = 0
            while (stepIndex < expandedSteps.size) {
                ensureActive()
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
                executor.executeStep(step, card, stepIndex)
                if (_state.value.status == SessionStatus.REVEALED) return@launch
                if (_state.value.interactionGate.kind != InteractionGateKind.NONE) return@launch
                if (step is NextCardStep) {
                    holding.first { !it }
                    advanceToNextCard()
                    return@launch
                }
                stepIndex++
            }
        }
        cardJobs[cardIndex] = job
        job.start()
    }

    fun rate(rating: Int) {
        if (_state.value.cardReviewed) return
        submitReview(rating)
        scope.launch { advanceToNextCard() }
    }

    /**
     * Zapis oceny (ręcznej i automatycznej) z pełną semantyką sekcji 6.5:
     * haptyka; rating 1 → failed; ponowna ocena w tej samej próbie NADPISUJE
     * (SRS liczony od bazy sprzed pierwszej oceny, heatmapa bez drugiego +1,
     * członkostwo w failed od nowa).
     */
    internal fun submitReview(rating: Int) {
        val s = _state.value
        if (s.cardReviewed) return
        val card = s.cards.getOrNull(s.currentCardIndex) ?: return
        deps.haptic(rating)
        if (reviewTracker.isReviewed(card.id)) {
            _state.value = sessionReducer(_state.value, SessionAction.RemoveFailedCard(card.id))
            val base = baseSrsStates[card.id] ?: card.srsState
            deps.onCardReviewed(group.id, card.id, rating, base)
        } else {
            baseSrsStates[card.id] = card.srsState
            reviewTracker.markReviewed(card.id)
            deps.onCardReviewed(group.id, card.id, rating, null)
        }
        if (rating == 1) {
            _state.value = sessionReducer(_state.value, SessionAction.AddFailedCard(card.id))
        }
        _state.value = sessionReducer(_state.value, SessionAction.SetCardReviewed)
    }

    private suspend fun advanceToNextCard() {
        holding.first { !it }
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
        holding.value = false
        scope.launch { advanceToNextCard() }
    }

    fun goToPreviousCard() {
        val s = _state.value
        if (s.status == SessionStatus.FINISHED) return
        cardJobs[s.currentCardIndex]?.cancel()
        holding.value = false
        peekTimerJob?.cancel()
        // <=3 s od startu karty: cofnij do poprzedniej; później: restart bieżącej od kroku 0
        val targetIndex = if (clock.now() - cardStartedAt <= StudyConstants.PREVIOUS_GOES_BACK_WINDOW_MS && s.currentCardIndex > 0) {
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
            is WaitForTapStep -> {
                delay(BackgroundSubstitution.calculateBgWaitMs(activePages))
                true
            }
            is WaitForTapToRevealNextStep -> {
                delay(BackgroundSubstitution.calculateBgWaitMs(activePages))
                val hidden = (0 until group.activePageCount).filter { it !in _state.value.revealedPages }
                hidden.firstOrNull()?.let {
                    _state.value = sessionReducer(_state.value, SessionAction.RevealNextPageInGate(it))
                }
                true
            }
            is WaitForTapToRevealStep -> {
                delay(BackgroundSubstitution.calculateBgWaitMs(activePages))
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

    /** Obsługa błędu TTS z executora: w tle auto-pauza zamiast statusu ERROR. */
    internal fun onTtsFailure(result: SpeakResult) {
        if (inBackground.value) {
            onBackgroundAutoPause?.invoke()
            cardJobs[_state.value.currentCardIndex]?.cancel()
        } else {
            val key = if (result == SpeakResult.MISSING_VOICE) ERROR_TTS_MISSING_VOICE else ERROR_TTS
            _state.value = sessionReducer(_state.value, SessionAction.SetError(key))
        }
    }

    internal fun onSttFailure() {
        _state.value = sessionReducer(_state.value, SessionAction.SetError(ERROR_STT))
    }

    fun clearError() {
        _state.value = sessionReducer(_state.value, SessionAction.ClearError)
    }

    /**
     * Uruchamia przerywalne tapem ciało kroku (TTS / czekanie / nasłuch).
     * Zwraca true, gdy krok został pominięty tapem.
     */
    internal suspend fun runSkippable(block: suspend () -> Unit): Boolean = coroutineScope {
        val inner = launch { block() }
        skippableJob = inner
        try {
            inner.join()
        } finally {
            if (skippableJob === inner) skippableJob = null
        }
        inner.isCancelled
    }

    /** Tap w kartę — tabela z sekcji 6.5 planu. */
    fun handleCardPress() {
        val s = _state.value
        when (s.status) {
            SessionStatus.REVEALED, SessionStatus.FINISHED -> Unit // NIC
            SessionStatus.LISTENING -> skippableJob?.cancel() // stop nasłuchu → wynik 'skipped'
            else -> { // SPEAKING / IDLE / ERROR
                if (s.interactionGate.kind == InteractionGateKind.TAP_TO_REVEAL) {
                    handleGateTap()
                } else {
                    skippableJob?.cancel() // skip kroku (przerwij audio/czekanie, idź dalej)
                }
            }
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
            TapRevealMode.NONE, null -> {
                _state.value = sessionReducer(_state.value, SessionAction.CompleteInteractionGate)
                runCurrentCard()
            }
        }
    }

    /**
     * Naciśnięcie karty (palec w dół). Po PEEK_HOLD_THRESHOLD_MS pokazuje podgląd
     * następnej ukrytej strony; trzymanie blokuje przejście do następnej karty.
     */
    fun pressDown() {
        holding.value = true
        holdStartedAt = clock.now()
        peekTimerJob?.cancel()
        val s = _state.value
        if (s.status == SessionStatus.REVEALED || s.status == SessionStatus.FINISHED) return
        val nextHidden = (0 until group.activePageCount).firstOrNull { it !in s.revealedPages }
        if (nextHidden != null) {
            peekTimerJob = scope.launch {
                delay(StudyConstants.PEEK_HOLD_THRESHOLD_MS)
                _state.value = sessionReducer(_state.value, SessionAction.PeekSet(nextHidden))
            }
        }
    }

    /**
     * Puszczenie karty: <500 ms → zwykły tap (trwałe odsłonięcie w gate / skip);
     * >=500 ms → schowanie podglądu (PEEK_CLEAR).
     */
    fun pressUp() {
        holding.value = false
        peekTimerJob?.cancel()
        peekTimerJob = null
        val heldMs = clock.now() - holdStartedAt
        if (heldMs >= StudyConstants.PEEK_HOLD_THRESHOLD_MS) {
            if (_state.value.peekedPageIndex != null) {
                _state.value = sessionReducer(_state.value, SessionAction.PeekClear)
            }
        } else {
            handleCardPress()
        }
    }

    fun setHolding(value: Boolean) { holding.value = value }

    fun pause() {
        // zawsze zwalnia hold (wiszący hold przy zejściu w tło — pułapka 5)
        holding.value = false
        peekTimerJob?.cancel()
        val s = _state.value
        if (s.status == SessionStatus.FINISHED) return
        // no-op gdy revealed/gate — czekamy na interakcję, nie ma czego pauzować
        if (s.status == SessionStatus.REVEALED || s.interactionGate.kind != InteractionGateKind.NONE) return
        cardJobs[s.currentCardIndex]?.cancel()
    }

    fun resume() {
        // bieżąca karta wciąż leci → nic nie rób (idempotentne, wołane też przez autostart playera)
        if (cardJobs[_state.value.currentCardIndex]?.isActive == true) return
        if (_state.value.status == SessionStatus.FINISHED) return
        // revealed/gate: czekamy na użytkownika, replay by zresetował stan karty
        if (_state.value.status == SessionStatus.REVEALED) return
        if (_state.value.interactionGate.kind != InteractionGateKind.NONE && !inBackground.value) return
        // replay bieżącej karty od kroku 0 (TTS/STT nie da się wznowić od połowy)
        _state.value = sessionReducer(_state.value, SessionAction.AdvanceCard(_state.value.currentCardIndex))
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    fun stop() {
        cardJobs.values.forEach { it.cancel() }
        cardJobs.clear()
        peekTimerJob?.cancel()
        _state.value = sessionReducer(_state.value, SessionAction.FinishSession)
    }

    fun end() {
        stop()
    }
}
