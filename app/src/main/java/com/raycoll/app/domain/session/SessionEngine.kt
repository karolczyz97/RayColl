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
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.activePageRange
import com.raycoll.app.domain.model.activePages
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

    private val reviews = ReviewLedger()
    val reviewedCardCount: Int get() = reviews.reviewedCount
    private val cardJobs = mutableMapOf<Int, Job>()
    private var lastExecutedCardIndex = -1
    private val holding = MutableStateFlow(false)
    private var cardStartedAt = 0L
    private var holdStartedAt = 0L
    private var peekTimerJob: Job? = null
    private var skippableJob: Job? = null
    private var sessionSequence = 0L
    private var visitSequence = 0L

    /** Numer przebiegu karty; wznowienie po pauzie go nie zmienia, ponowne wejście na kartę tak. */
    internal val visit: Long get() = visitSequence
    private var navigationSequence = 0L
    private var navigationGestures = 0

    internal fun dispatch(action: SessionAction) = _state.update { sessionReducer(it, action) }

    fun start(cards: List<Flashcard>) {
        cancelCardJobs()
        _isPaused.value = false
        holding.value = false
        reviews.clear()
        executor.reset()
        visitSequence++
        dispatch(SessionAction.StartSession(cards, ++sessionSequence))
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
        val visit = visitSequence
        val job = scope.launch(start = CoroutineStart.LAZY) { runSteps(card, visit) }
        cardJobs[cardIndex] = job
        job.start()
    }

    private suspend fun runSteps(card: Flashcard, visit: Long) {
        var stepIndex = _state.value.stepIndex
        while (stepIndex < expandedSteps.size) {
            _state.first { it.navigation == null }
            currentCoroutineContext().ensureActive()
            if (visit != visitSequence || _state.value.status == SessionStatus.FINISHED) return
            _state.value.pendingStepIndexToRun?.let { pending ->
                dispatch(SessionAction.ConsumePendingStepIndex)
                stepIndex = pending
                continue
            }
            val step = expandedSteps[stepIndex]
            // niespełniony warunek = krok pominięty, także "następna karta" z drugiej gałęzi
            if (!executor.isConditionMet(step)) {
                stepIndex++
                dispatch(SessionAction.SetStepIndex(stepIndex))
                continue
            }
            if (_inBackground.value && substituteInBackground(step, card)) {
                dispatch(SessionAction.SetStepIndex(stepIndex + 1))
                if (_state.value.status == SessionStatus.AWAITING_RATING) return
                stepIndex++
                continue
            }
            executor.execute(step, card, stepIndex)
            currentCoroutineContext().ensureActive()
            dispatch(SessionAction.SetStepIndex(stepIndex + 1))
            if (_state.value.status == SessionStatus.AWAITING_RATING) return
            if (_state.value.interactionGate.kind != InteractionGateKind.NONE) return
            if (step is NextCardStep) {
                advanceToNextCard(visit)
                return
            }
            stepIndex++
        }
    }

    fun rate(rating: Int) {
        val s = _state.value
        if (s.navigation != null || s.status != SessionStatus.AWAITING_RATING ||
            (s.cardReviewed && !s.revisiting)
        ) {
            return
        }
        interruptCurrentCard()
        submitReview(rating, allowReplacement = s.revisiting)
        requestAdvance()
    }

    /**
     * Zapis oceny (ręcznej i automatycznej): haptyka, ocena 1 = karta na liście
     * nieudanych. Ponowna ocena tej samej karty w tej próbie nadpisuje poprzednią.
     */
    internal fun submitReview(rating: Int, allowReplacement: Boolean = false) {
        val s = _state.value
        if (s.cardReviewed && !allowReplacement) return
        val card = s.cards.getOrNull(s.currentCardIndex) ?: return
        deps.haptic(rating)
        val base = reviews.previousBase(card)
        if (base != null) dispatch(SessionAction.RemoveFailedCard(card.id))
        deps.onCardReviewed(group.id, card.id, rating, base)
        if (rating == Ratings.AGAIN) dispatch(SessionAction.AddFailedCard(card.id))
        // ten sam wynik FSRS, który zapisuje repozytorium — liczony lokalnie, bez czytania z chmury
        val reviewed = card.reviewed(rating, clock.now(), reviews.base(card))
        dispatch(SessionAction.UpdateCardSrs(card.id, reviewed.srsState))
        dispatch(SessionAction.SetCardReviewed)
    }

    private fun requestAdvance() {
        val visit = visitSequence
        scope.launch { advanceToNextCard(visit) }
    }

    private suspend fun advanceToNextCard(visit: Long) {
        holding.first { !it }
        _state.first { it.navigation == null }
        if (visit != visitSequence || _state.value.status == SessionStatus.FINISHED) return
        val nextIndex = _state.value.currentCardIndex + 1
        if (nextIndex >= _state.value.cards.size) {
            visitSequence++
            dispatch(SessionAction.FinishSession)
            return
        }
        visitCard(nextIndex)
    }

    fun skipToNextCard() {
        if (_state.value.status == SessionStatus.FINISHED || _state.value.navigation != null) return
        interruptCurrentCard()
        requestAdvance()
    }

    /** Przycisk „poprzednia”: po 3 s restartuje kartę, wcześniej wraca do zapamiętanego stanu poprzedniej. */
    fun goToPreviousCard() {
        val s = _state.value
        if (s.status == SessionStatus.FINISHED || s.navigation != null) return
        val withinWindow = clock.now() - cardStartedAt <= StudyConstants.PREVIOUS_GOES_BACK_WINDOW_MS
        if (withinWindow && s.currentCardIndex > 0) {
            visitCard(s.currentCardIndex - 1)
        } else {
            replayCard(s.currentCardIndex)
        }
    }

    fun beginNavigation(sessionId: Long, sourceCardId: String): CardNavigation? {
        val s = _state.value
        if (s.sessionId != sessionId || s.cards.getOrNull(s.currentCardIndex)?.id != sourceCardId ||
            s.status == SessionStatus.FINISHED || s.navigation != null
        ) {
            return null
        }
        val navigation = CardNavigation(sessionId, sourceCardId, ++navigationSequence)
        navigationGestures = 1
        dispatch(SessionAction.BeginNavigation(navigation))
        cancelPress()
        return navigation
    }

    /** Następny gest może zacząć się przed końcem poprzedniej animacji; podglądy pozostają bierne. */
    fun continueNavigation(navigation: CardNavigation): Boolean {
        if (_state.value.navigation != navigation) return false
        navigationGestures++
        return true
    }

    /** null = odwołany gest. Zmiana strony jest rozstrzygana raz, na podstawie stabilnych ID. */
    fun endNavigation(navigation: CardNavigation, destination: CardDestination?) {
        val s = _state.value
        if (s.navigation != navigation) return
        val index = when (destination) {
            is CardDestination.Card -> s.cards.indexOfFirst { it.id == destination.id }
            CardDestination.Summary -> s.cards.size
            null -> s.currentCardIndex
        }
        val distance = kotlin.math.abs(index - s.currentCardIndex)
        if (index < 0 || distance > navigationGestures || index == s.currentCardIndex) {
            dispatch(SessionAction.EndNavigation(navigation))
        } else if (index == s.cards.size) {
            stop()
        } else {
            visitCard(index)
        }
    }

    private fun visitCard(index: Int) {
        interruptCurrentCard()
        visitSequence++
        dispatch(SessionAction.VisitCard(index))
        lastExecutedCardIndex = -1
        cardStartedAt = clock.now()
        val s = _state.value
        if (s.status != SessionStatus.AWAITING_RATING && s.interactionGate.kind == InteractionGateKind.NONE) {
            runCurrentCard()
        }
    }

    fun restartCurrentCard() {
        val s = _state.value
        if (s.status == SessionStatus.FINISHED) return
        replayCard(s.currentCardIndex)
    }

    private fun replayCard(index: Int) {
        interruptCurrentCard()
        visitSequence++
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
            runSkippable { delay(BackgroundSubstitution.calculateBgWaitMs(card.activePages(group.activePageCount))) }
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

    internal fun hiddenPages(): List<Int> = group.activePageRange.filter { it !in _state.value.revealedPages }

    fun setBackground(value: Boolean) {
        _inBackground.value = value
        if (value) cancelNavigation()
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
        if (_isPaused.value || _state.value.navigation != null) return
        val s = _state.value
        when (s.status) {
            SessionStatus.AWAITING_RATING, SessionStatus.FINISHED -> Unit

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
        if (s.navigation != null || s.status == SessionStatus.AWAITING_RATING ||
            s.status == SessionStatus.FINISHED
        ) {
            return
        }
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

    private fun cancelNavigation() {
        _state.value.navigation?.let { dispatch(SessionAction.EndNavigation(it)) }
    }

    fun pause() {
        _isPaused.value = true
        // zawsze puszcza trzymanie — inaczej po powrocie karta czekałaby na puszczenie palca
        releaseHold()
        val s = _state.value
        // oceny/bramka czekają na użytkownika — pozostałe kroki trzeba zatrzymać przed zdjęciem blokady gestu
        if (s.status != SessionStatus.AWAITING_RATING && s.interactionGate.kind == InteractionGateKind.NONE) {
            cardJobs[s.currentCardIndex]?.cancel()
        }
        if (s.status == SessionStatus.SPEAKING || s.status == SessionStatus.LISTENING) {
            dispatch(SessionAction.EndSpeaking)
        }
        cancelNavigation()
    }

    /**
     * Wznawia przerwaną kartę od przerwanego kroku, z zachowanym stanem karty (odsłonięte strony, wynik odpowiedzi,
     * ocena). Mowy ani nasłuchu nie da się podjąć w połowie, więc ten jeden krok rusza od początku;
     * czekanie wraca z czasem, który mu został.
     */
    fun resume() {
        _isPaused.value = false
        val s = _state.value
        if (cardJobs[s.currentCardIndex]?.isActive == true) return
        if (s.status == SessionStatus.FINISHED || s.status == SessionStatus.AWAITING_RATING) return
        if (s.interactionGate.kind != InteractionGateKind.NONE && !_inBackground.value) return
        // bramka czekająca na tapnięcie (sesja zeszła w tło) dostaje zastępstwa od początku karty
        if (s.interactionGate.kind == InteractionGateKind.NONE && s.stepIndex in expandedSteps.indices) {
            lastExecutedCardIndex = -1
            runCurrentCard()
        } else {
            replayCard(s.currentCardIndex)
        }
    }

    fun stop() {
        cancelCardJobs()
        visitSequence++
        _isPaused.value = false
        dispatch(SessionAction.FinishSession)
    }

    private fun cancelCardJobs() {
        cardJobs.values.forEach { it.cancel() }
        cardJobs.clear()
        peekTimerJob?.cancel()
    }
}
