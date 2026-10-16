package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.referencedPage
import com.raycoll.app.domain.operations.reviewed
import com.raycoll.app.domain.steps.CompoundExpander
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sesja nauki jednej talii: przechodzi karta po karcie przez kroki trybu,
 * obsługuje tapnięcia, podgląd strony (przytrzymanie), oceny i pracę w tle.
 */
class SessionEngine(
    private val group: FlashcardGroup,
    steps: List<ModeStep>,
    private val deps: SessionDeps,
    private val clock: Clock,
    ttsRate: Float,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _inBackground = MutableStateFlow(false)
    val inBackground: StateFlow<Boolean> = _inBackground.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // kroki dotyczące stron, których talia nie pokazuje, są pomijane
    private val expandedSteps: List<ModeStep> = CompoundExpander.expandModeSteps(steps).filter { step ->
        val page = step.referencedPage
        page == null || page < group.activePageCount
    }
    private val executor = StepExecutor(this, deps, group, clock, ttsRate)
    val needsMicrophone: Boolean = expandedSteps.any { it is ListenAndCheckStep }
    val needsAudio: Boolean = expandedSteps.any {
        it is SpeakPageStep || it is SpeakAllPagesStep || it is ListenAndCheckStep
    }
    val canRunHandsFree: Boolean = BackgroundSubstitution.isModeHandsFreeCapable(expandedSteps)

    // stan SRS sprzed pierwszej oceny w tej próbie — baza dla ponownej (nadpisującej) oceny;
    // klucze = karty ocenione w tej sesji
    private val baseSrsStates = mutableMapOf<String, SrsState>()
    val reviewedCardCount: Int get() = baseSrsStates.size
    private val cardJobs = mutableMapOf<Int, Job>()
    private var lastExecutedCardIndex = -1
    private val holding = MutableStateFlow(false)
    private var cardStartedAt = 0L
    private var holdStartedAt = 0L
    private var peekTimerJob: Job? = null
    private var skippableJob: Job? = null

    internal fun dispatch(action: SessionAction) = _state.update { sessionReducer(it, action) }

    fun start(cards: List<Flashcard>) {
        cancelCardJobs()
        _isPaused.value = false
        holding.value = false
        baseSrsStates.clear()
        dispatch(SessionAction.StartSession(cards))
        if (cards.isEmpty()) {
            dispatch(SessionAction.FinishSession)
            return
        }
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    private fun runCurrentCard() {
        if (_isPaused.value) return
        val s = _state.value
        val cardIndex = s.currentCardIndex
        if (cardIndex !in s.cards.indices) return
        // tę samą kartę wznawiamy tylko, gdy czeka zaległy krok (po tapnięciu w bramkę)
        if (cardIndex == lastExecutedCardIndex && s.pendingStepIndexToRun == null) return
        lastExecutedCardIndex = cardIndex
        // start karty liczy się od kroku 0; wznowienie po bramce nie przesuwa okna "cofnij do poprzedniej"
        if (s.pendingStepIndexToRun == null) cardStartedAt = clock.now()

        cardJobs[cardIndex]?.cancel()
        // LAZY: job musi trafić do mapy, zanim ruszy — przy natychmiastowym dispatcherze
        // ciało wykonałoby się przed przypisaniem i anulowanie trafiałoby w stary job
        val card = s.cards[cardIndex]
        val job = scope.launch(start = CoroutineStart.LAZY) { runSteps(card) }
        cardJobs[cardIndex] = job
        job.start()
    }

    private suspend fun runSteps(card: Flashcard) {
        var stepIndex = 0
        while (stepIndex < expandedSteps.size) {
            currentCoroutineContext().ensureActive()
            if (_state.value.status == SessionStatus.FINISHED) return
            _state.value.pendingStepIndexToRun?.let { pending ->
                dispatch(SessionAction.ConsumePendingStepIndex)
                stepIndex = pending
                continue
            }
            val step = expandedSteps[stepIndex]
            // niespełniony warunek = krok pominięty, także "następna karta" z drugiej gałęzi
            if (!executor.isConditionMet(step)) {
                stepIndex++
                continue
            }
            if (_inBackground.value && substituteInBackground(step, card)) {
                if (_state.value.status == SessionStatus.REVEALED) return
                stepIndex++
                continue
            }
            executor.execute(step, card, stepIndex)
            if (_state.value.status == SessionStatus.REVEALED) return
            if (_state.value.interactionGate.kind != InteractionGateKind.NONE) return
            if (step is NextCardStep) {
                advanceToNextCard()
                return
            }
            stepIndex++
        }
    }

    fun rate(rating: Int) {
        if (_state.value.status != SessionStatus.REVEALED || _state.value.cardReviewed) return
        interruptCurrentCard()
        submitReview(rating)
        scope.launch { advanceToNextCard() }
    }

    /**
     * Zapis oceny (ręcznej i automatycznej): haptyka, ocena 1 = karta na liście
     * nieudanych. Ponowna ocena tej samej karty w tej próbie nadpisuje poprzednią.
     */
    internal fun submitReview(rating: Int) {
        val s = _state.value
        if (s.cardReviewed) return
        val card = s.cards.getOrNull(s.currentCardIndex) ?: return
        deps.haptic(rating)
        val base = baseSrsStates[card.id]
        if (base != null) {
            dispatch(SessionAction.RemoveFailedCard(card.id))
            deps.onCardReviewed(group.id, card.id, rating, base)
        } else {
            baseSrsStates[card.id] = card.srsState
            deps.onCardReviewed(group.id, card.id, rating, null)
        }
        if (rating == Ratings.AGAIN) dispatch(SessionAction.AddFailedCard(card.id))
        // ten sam wynik FSRS, który zapisuje repozytorium — liczony lokalnie, bez czytania z chmury
        val reviewed = card.reviewed(rating, clock.now(), baseSrsStates.getValue(card.id))
        dispatch(SessionAction.UpdateCardSrs(card.id, reviewed.srsState))
        dispatch(SessionAction.SetCardReviewed)
    }

    private suspend fun advanceToNextCard() {
        holding.first { !it }
        if (_state.value.status == SessionStatus.FINISHED) return
        val nextIndex = _state.value.currentCardIndex + 1
        if (nextIndex >= _state.value.cards.size) {
            dispatch(SessionAction.FinishSession)
            return
        }
        dispatch(SessionAction.AdvanceCard(nextIndex))
        runCurrentCard()
    }

    fun skipToNextCard() {
        if (_state.value.status == SessionStatus.FINISHED) return
        interruptCurrentCard()
        scope.launch { advanceToNextCard() }
    }

    /** Do 3 s od startu karty: powrót do poprzedniej; później: bieżąca od początku. */
    fun goToPreviousCard() {
        val s = _state.value
        if (s.status == SessionStatus.FINISHED) return
        val withinWindow = clock.now() - cardStartedAt <= StudyConstants.PREVIOUS_GOES_BACK_WINDOW_MS
        replayCard(if (withinWindow && s.currentCardIndex > 0) s.currentCardIndex - 1 else s.currentCardIndex)
    }

    fun restartCurrentCard() {
        val s = _state.value
        if (s.status == SessionStatus.FINISHED) return
        replayCard(s.currentCardIndex)
    }

    private fun replayCard(index: Int) {
        interruptCurrentCard()
        dispatch(SessionAction.AdvanceCard(index))
        lastExecutedCardIndex = -1
        runCurrentCard()
    }

    private fun interruptCurrentCard() {
        cardJobs[_state.value.currentCardIndex]?.cancel()
        releaseHold()
    }

    /**
     * Zastępstwa kroków wymagających użytkownika, gdy sesja idzie w tle.
     * Zwraca true, gdy krok został obsłużony zastępczo.
     */
    private suspend fun substituteInBackground(step: ModeStep, card: Flashcard): Boolean {
        suspend fun waitForReading() =
            runSkippable { delay(BackgroundSubstitution.calculateBgWaitMs(card.pages.take(group.activePageCount))) }
        return when (step) {
            is WaitForTapStep -> {
                waitForReading()
                true
            }

            is WaitForTapToRevealNextStep -> {
                waitForReading()
                hiddenPages().firstOrNull()?.let { dispatch(SessionAction.RevealPages(listOf(it))) }
                true
            }

            is WaitForTapToRevealStep -> {
                waitForReading()
                hiddenPages().takeIf { it.isNotEmpty() }?.let { dispatch(SessionAction.RevealPages(it)) }
                true
            }

            // ocena wymaga użytkownika: pokaż oceny i wstrzymaj sesję do powrotu do aplikacji
            is ShowRatingsStep -> {
                if (!_state.value.cardReviewed) {
                    dispatch(SessionAction.ShowRatings)
                }
                true
            }

            else -> false
        }
    }

    internal fun hiddenPages(): List<Int> = (0 until group.activePageCount).filter { it !in _state.value.revealedPages }

    fun setBackground(value: Boolean) {
        _inBackground.value = value
        if (value && !_isPaused.value && _state.value.interactionGate.kind != InteractionGateKind.NONE) {
            resume()
        }
    }

    /** Błąd TTS: w tle sesja staje (nikt nie zobaczy komunikatu), na ekranie — komunikat i dalej. */
    internal fun onTtsFailure(result: SpeakResult) {
        if (_inBackground.value) {
            pause()
        } else {
            val error = if (result == SpeakResult.MISSING_VOICE) SessionError.TTS_MISSING_VOICE else SessionError.TTS
            dispatch(SessionAction.SetError(error))
        }
    }

    internal fun onSttFailure() = dispatch(SessionAction.SetError(SessionError.STT))

    fun clearError() = dispatch(SessionAction.ClearError)

    /**
     * Uruchamia część kroku, którą tapnięcie może przerwać (mowa, pauza, nasłuch).
     * Zwraca wynik [block] albo null, gdy przerwano go tapnięciem.
     */
    internal suspend fun <T> runSkippable(block: suspend () -> T): T? = coroutineScope {
        var result: T? = null
        val inner = launch { result = block() }
        skippableJob = inner
        try {
            inner.join()
        } finally {
            if (skippableJob === inner) skippableJob = null
        }
        if (inner.isCancelled) null else result
    }

    /** Tapnięcie w kartę. */
    fun handleCardPress() {
        if (_isPaused.value) return
        val s = _state.value
        when (s.status) {
            SessionStatus.REVEALED, SessionStatus.FINISHED -> Unit

            // koniec nasłuchu = odpowiedź pominięta
            SessionStatus.LISTENING -> skippableJob?.cancel()

            SessionStatus.IDLE, SessionStatus.SPEAKING, SessionStatus.ERROR -> handleTapWhileIdle()
        }
    }

    private fun handleTapWhileIdle() {
        if (_state.value.interactionGate.kind == InteractionGateKind.TAP_TO_REVEAL) {
            handleGateTap()
        } else {
            skippableJob?.cancel()
        }
    }

    private fun handleGateTap() {
        val gate = _state.value.interactionGate
        val hidden = hiddenPages()
        val reveals = gate.revealMode == TapRevealMode.SINGLE || gate.revealMode == TapRevealMode.REMAINING
        if (reveals) hidden.firstOrNull()?.let { dispatch(SessionAction.RevealPages(listOf(it))) }
        // "odsłaniaj pozostałe" czeka na kolejne tapnięcia, dopóki coś jest ukryte
        val gateDone = gate.revealMode != TapRevealMode.REMAINING || hidden.size <= 1
        if (gateDone) {
            dispatch(SessionAction.CompleteInteractionGate)
            runCurrentCard()
        }
    }

    /**
     * Palec na karcie. Po [StudyConstants.PEEK_HOLD_THRESHOLD_MS] pokazuje podgląd
     * następnej ukrytej strony; trzymanie wstrzymuje przejście do następnej karty.
     */
    fun pressDown() {
        val s = _state.value
        if (s.status == SessionStatus.REVEALED || s.status == SessionStatus.FINISHED) return
        holding.value = true
        holdStartedAt = clock.now()
        peekTimerJob?.cancel()
        // ukryta strona liczona po opóźnieniu: w międzyczasie krok mógł już odsłonić tę wybraną przy naciśnięciu
        peekTimerJob = scope.launch {
            delay(StudyConstants.PEEK_HOLD_THRESHOLD_MS)
            hiddenPages().firstOrNull()?.let { dispatch(SessionAction.PeekSet(it)) }
        }
    }

    /** Puszczenie karty: krótkie = zwykłe tapnięcie, długie = schowanie podglądu. */
    fun pressUp() {
        releaseHold()
        if (clock.now() - holdStartedAt >= StudyConstants.PEEK_HOLD_THRESHOLD_MS) {
            clearPeek()
        } else {
            handleCardPress()
        }
    }

    /** Anulowanie naciśnięcia (np. przewijanie): chowa podgląd, nie działa jak tapnięcie. */
    fun cancelPress() {
        releaseHold()
        clearPeek()
    }

    private fun releaseHold() {
        holding.value = false
        peekTimerJob?.cancel()
        peekTimerJob = null
    }

    private fun clearPeek() {
        if (_state.value.peekedPageIndex != null) dispatch(SessionAction.PeekClear)
    }

    fun pause() {
        _isPaused.value = true
        // zawsze puszcza trzymanie — inaczej po powrocie karta czekałaby na puszczenie palca
        releaseHold()
        val s = _state.value
        // oceny/bramka czekają na użytkownika — nie ma czego pauzować
        if (s.status == SessionStatus.FINISHED || s.status == SessionStatus.REVEALED) return
        if (s.interactionGate.kind != InteractionGateKind.NONE) return
        cardJobs[s.currentCardIndex]?.cancel()
        if (s.status == SessionStatus.SPEAKING || s.status == SessionStatus.LISTENING) {
            dispatch(SessionAction.EndSpeaking)
        }
    }

    /** Wznawia przerwaną kartę od kroku 0 (mowy ani nasłuchu nie da się podjąć w połowie). */
    fun resume() {
        _isPaused.value = false
        val s = _state.value
        if (cardJobs[s.currentCardIndex]?.isActive == true) return
        if (s.status == SessionStatus.FINISHED || s.status == SessionStatus.REVEALED) return
        if (s.interactionGate.kind != InteractionGateKind.NONE && !_inBackground.value) return
        replayCard(s.currentCardIndex)
    }

    fun stop() {
        cancelCardJobs()
        _isPaused.value = false
        dispatch(SessionAction.FinishSession)
    }

    private fun cancelCardJobs() {
        cardJobs.values.forEach { it.cancel() }
        cardJobs.clear()
        peekTimerJob?.cancel()
    }
}
