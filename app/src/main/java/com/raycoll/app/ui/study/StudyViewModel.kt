package com.raycoll.app.ui.study

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.selectors.getDueCards
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionError
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.playback.StudySessionHolder
import com.raycoll.app.ui.components.UiEvents
import com.raycoll.app.ui.navigation.StudyRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StudyUiState {
    data object Loading : StudyUiState

    data object DeckMissing : StudyUiState

    data class NoDueCards(val deckName: String) : StudyUiState

    data class Active(
        val deckName: String,
        val session: SessionState,
        /** Ile stron talii jest aktywnych — pozostałe strony kart są ukryte (zostają w danych). */
        val activePageCount: Int,
        /** Sesja przejdzie w tryb "bez rąk" po zejściu aplikacji w tło (potrzebne powiadomienia). */
        val runsInBackground: Boolean,
        /** Tryb używa mikrofonu. */
        val needsMicrophone: Boolean,
        /** Użytkownik zakończył sesję przed ostatnią kartą. */
        val endedEarly: Boolean,
        val paused: Boolean = false,
        val resultCards: List<Flashcard>? = null,
        val reviewedCardCount: Int = 0,
        /** Karty przerobione do końca: przy przerwaniu tylko te przed bieżącą. */
        val completedCardCount: Int = 0
    ) : StudyUiState {
        val finished: Boolean get() = session.status == SessionStatus.FINISHED
        val currentCard: Flashcard? get() = session.cards.getOrNull(session.currentCardIndex)
        val inProgress: Boolean get() = !finished && currentCard != null
    }
}

sealed interface StudyEvent {
    data class SpeechError(val error: SessionError) : StudyEvent

    data object AudioFocusDenied : StudyEvent
}

/** Jedna sesja nauki talii; sesja żyje tyle co ten ViewModel (przetrwa obrót ekranu). */
@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val modeRepository: StudyModeRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val sessionHolder: StudySessionHolder,
    private val sessionDeps: SessionDeps,
    private val clock: Clock
) : ViewModel() {
    val deckId: String = savedStateHandle.toRoute<StudyRoute>().deckId

    private val _uiState = MutableStateFlow<StudyUiState>(StudyUiState.Loading)
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val eventSink = UiEvents<StudyEvent>()
    val events: Flow<StudyEvent> = eventSink.flow

    private var engine: SessionEngine? = null
    private var deckName = ""

    // tryb pozwala na pracę w tle (ustawienie + kroki trybu); runsInBackground — czy serwis faktycznie ruszył
    private var backgroundAllowed = false
    private var runsInBackground = false
    private var pausedByAppBackground = false
    private var restartJob: Job? = null

    // nasłuch nie wyszedł (np. brak zgody na mikrofon) — po udzieleniu zgody karta rusza od nowa
    private var listeningFailed = false

    // zejście całej aplikacji w tło (nie obrót ekranu): sesja idzie dalej "bez rąk" albo staje
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = onAppBackground()

        override fun onStart(owner: LifecycleOwner) = onAppForeground()
    }

    init {
        viewModelScope.launch {
            sessionHolder.focusDenied.collect { denied ->
                if (denied) {
                    if (sessionHolder.appVisible()) eventSink.send(StudyEvent.AudioFocusDenied)
                    sessionHolder.clearFocusError()
                }
            }
        }
        viewModelScope.launch { startSession() }
    }

    private suspend fun startSession() {
        val deck = deckRepository.getDeck(deckId)
        if (deck == null) {
            _uiState.value = StudyUiState.DeckMissing
            return
        }
        val cards = getDueCards(deck.cards, deck.studyFilter, deck.cardOrder, clock.now())
        if (cards.isEmpty()) {
            _uiState.value = StudyUiState.NoDueCards(deck.name)
            return
        }

        deckRepository.markStudied(deckId)
        val steps = modeRepository.getModeOrDefault(deck.activeModeId).steps
        val preferences = prefsRepository.preferences.first()
        val engine = SessionEngine(deck, steps, sessionDeps, clock, preferences.ttsRate, viewModelScope)
        this.engine = engine
        this.deckName = deck.name
        backgroundAllowed = preferences.backgroundPlayback && engine.canRunHandsFree
        runsInBackground = backgroundAllowed
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        viewModelScope.launch {
            // zmiana stanu kolejki zapisów (np. ponowienie po błędzie) przelicza też podsumowanie końca sesji
            combine(engine.state, engine.isPaused) { session, paused ->
                session to paused
            }.collectLatest { (session, paused) ->
                session.error?.let { error ->
                    if (error == SessionError.STT) listeningFailed = true
                    eventSink.send(StudyEvent.SpeechError(error))
                    engine.clearError()
                }
                _uiState.update { current ->
                    activeState(engine, deck.activePageCount, session, paused, current as? StudyUiState.Active)
                }
                if (session.status == SessionStatus.FINISHED) refreshResultCards(session)
            }
        }
        // przebieg musi już trwać, zanim serwis w tle zacznie go obserwować (zakończony by go zamknął)
        sessionHolder.prepare(engine, deckName)
        engine.start(cards)
        registerSession(engine)
    }

    private fun activeState(
        engine: SessionEngine,
        activePageCount: Int,
        session: SessionState,
        paused: Boolean,
        previous: StudyUiState.Active?
    ): StudyUiState.Active {
        val finished = session.status == SessionStatus.FINISHED
        val endedEarly = previous?.endedEarly == true && finished
        return StudyUiState.Active(
            deckName = deckName,
            session = session,
            paused = paused,
            activePageCount = activePageCount,
            runsInBackground = runsInBackground,
            needsMicrophone = engine.needsMicrophone,
            reviewedCardCount = engine.reviewedCardCount,
            resultCards = previous?.resultCards.takeIf { finished },
            endedEarly = endedEarly,
            completedCardCount = previous?.takeIf { endedEarly }?.completedCardCount ?: session.cards.size
        )
    }

    /** Karty podsumowania czytane z bazy dopiero po zapisie wszystkich ocen. */
    private suspend fun refreshResultCards(session: SessionState) {
        val ids = session.cards.map { it.id }.toSet()
        val fresh = deckRepository.getDeck(deckId)?.cards?.filter { it.id in ids }.orEmpty()
        _uiState.update { current ->
            (current as? StudyUiState.Active)?.copy(resultCards = fresh) ?: current
        }
    }

    /**
     * Oddaje silnik serwisowi w tle. Gdy serwis nie ruszył albo aplikacja zdążyła już zejść w tło
     * (start trwał dłużej niż przełączenie), sesja przechodzi w tło tak jak przy zwykłym zejściu.
     */
    private fun registerSession(engine: SessionEngine) {
        val started = sessionHolder.register(engine, deckName, backgroundAllowed)
        runsInBackground = backgroundAllowed && started
        _uiState.update { (it as? StudyUiState.Active)?.copy(runsInBackground = runsInBackground) ?: it }
        if (!sessionHolder.appVisible()) onAppBackground()
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

    fun tapCard() {
        engine?.handleCardPress()
    }

    fun resume() {
        sessionHolder.resume()
    }

    fun pressDown() {
        engine?.pressDown()
    }

    fun pressUp() {
        engine?.pressUp()
    }

    fun cancelPress() {
        engine?.cancelPress()
    }

    /** Po zgodzie na mikrofon karta, której nasłuch się nie udał, rusza od nowa. */
    fun onMicrophoneGranted() {
        if (!listeningFailed) return
        listeningFailed = false
        engine?.restartCurrentCard()
    }

    /** "Od nowa": wszystkie karty sesji w aktualnym stanie z bazy. */
    fun restartSession() = restartWith { session -> session.cards.map { it.id } }

    /** "Powtórz nieudane": tylko karty ocenione jako "jeszcze raz". */
    fun restartFailed() = restartWith { session -> session.cards.map { it.id }.filter { it in session.failedCardIds } }

    private fun restartWith(selectIds: (SessionState) -> List<String>) {
        val engine = engine ?: return
        if (restartJob?.isActive == true) return
        val ids = selectIds(engine.state.value)
        restartJob = viewModelScope.launch {
            val fresh = deckRepository.getDeck(deckId)?.cards?.associateBy { it.id } ?: return@launch
            val cards = ids.mapNotNull(fresh::get)
            if (cards.isEmpty()) return@launch
            _uiState.update { (it as? StudyUiState.Active)?.copy(endedEarly = false) ?: it }
            // Restart zachowuje ustawienia/kroki bieżącej sesji; zmiany talii obowiązują od nowej sesji.
            // ten sam silnik: najpierw nowy przebieg, potem ponowne oddanie go serwisowi w tle
            // (stary serwis zamknął się przy końcu poprzedniej próby razem z powiadomieniem)
            sessionHolder.prepare(engine, deckName)
            engine.start(cards)
            registerSession(engine)
        }
    }

    /** Zakończenie przed ostatnią kartą — podsumowanie mówi wtedy "sesja przerwana". */
    fun endSessionEarly() {
        if (engine?.state?.value?.status == SessionStatus.FINISHED) return
        _uiState.update {
            (it as? StudyUiState.Active)?.copy(endedEarly = true, completedCardCount = it.session.currentCardIndex)
                ?: it
        }
        engine?.stop()
    }

    internal fun onAppBackground() {
        val engine = engine ?: return
        if (engine.state.value.status == SessionStatus.FINISHED) return
        if (runsInBackground) {
            engine.setBackground(true)
        } else if (!engine.isPaused.value) {
            // wznawiamy po powrocie tylko to, co sami wstrzymaliśmy
            pausedByAppBackground = true
            engine.pause()
        }
    }

    internal fun onAppForeground() {
        val engine = engine ?: return
        engine.setBackground(false)
        if (pausedByAppBackground) {
            pausedByAppBackground = false
            sessionHolder.resume()
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        engine?.let(sessionHolder::release)
    }
}
