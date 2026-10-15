package com.raycoll.app.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.raycoll.app.di.ApplicationScope
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "StudySessionHolder"

/** Aktywna sesja nauki widziana przez cały proces. */
data class ActiveSession(val engine: SessionEngine, val deckName: String)

internal data class SessionBoundary(val attempt: Long = 0, val ended: Boolean = false)

/**
 * Jedyny właściciel aktywnej sesji: ekran nauki ją rejestruje, serwis w tle
 * i synchronizacja ją obserwują. Rejestracja sesji, która może iść w tle,
 * uruchamia serwis; zwolnienie sesji zatrzymuje go (serwis kończy się sam).
 * Wszystko poza serwisem dzieje się na wątku głównym.
 */
@Singleton
class StudySessionHolder internal constructor(
    private val context: Context,
    private val startService: (Context, Intent) -> Unit,
    private val isAppVisible: () -> Boolean,
    private val focusRequester: AudioFocusRequester = AudioFocusManager(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        audioFocusManager: AudioFocusManager,
        @ApplicationScope scope: CoroutineScope
    ) : this(
        context,
        { ctx, intent -> ContextCompat.startForegroundService(ctx, intent) },
        { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
        audioFocusManager,
        scope
    )

    internal constructor(context: Context) : this(
        context,
        { ctx, intent -> ContextCompat.startForegroundService(ctx, intent) },
        { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
    )

    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()
    private var focus: SessionAudioFocus? = null
    private var audioJob: Job? = null
    private var backgroundPlaybackReady = false
    private val _focusDenied = MutableStateFlow(false)
    val focusDenied: StateFlow<Boolean> = _focusDenied.asStateFlow()
    private val _boundary = MutableStateFlow(SessionBoundary())
    internal val boundary: StateFlow<SessionBoundary> = _boundary.asStateFlow()

    val controls: SessionControls = object : SessionControls {
        override fun play(): Boolean {
            val engine = current() ?: return false
            val playing = if (engine.needsAudio) {
                focus?.play() == true
            } else {
                engine.resume()
                true
            }
            if (!playing) _focusDenied.value = true
            return playing
        }

        override fun pause() {
            focus?.pause() ?: current()?.pause()
        }

        override fun next() {
            current()?.skipToNextCard()
        }
        override fun previous() {
            current()?.goToPreviousCard()
        }
        override fun stop() {
            current()?.let(::release)
        }
    }
    private val audioRoute = AudioRouteObserver(context, controls::pause)

    fun resume(): Boolean = controls.play()

    fun clearFocusError() {
        _focusDenied.value = false
    }

    /** Wołane przed każdym wywołaniem TTS/STT, również po tapnięciu w bramkę lub zmianie karty. */
    fun acquireAudioFocus(): Boolean {
        val granted = focus?.onSessionPlaying() == true
        if (!granted) _focusDenied.value = true
        return granted
    }

    /** Rozpoznawanie trzyma własny fokus; kolejne wyjście audio pozyskuje nasz fokus ponownie. */
    suspend fun <T> withRecognitionFocus(block: suspend () -> T): T {
        val sessionFocus = focus ?: return block()
        return sessionFocus.withRecognitionFocus(block)
    }

    /** Wiąże fokus przed startem kroków; serwis uruchamiamy dopiero po engine.start. */
    fun prepare(engine: SessionEngine, deckName: String) {
        audioJob?.cancel()
        focus?.release()
        audioRoute.stop()
        _session.value?.engine?.takeIf { it !== engine }?.stop()
        _boundary.value = SessionBoundary(_boundary.value.attempt + 1)
        _session.value = ActiveSession(engine, deckName)
        _focusDenied.value = false
        backgroundPlaybackReady = false
        focus = SessionAudioFocus(
            focusRequester,
            object : FocusedSession {
                override fun resume() {
                    if (appVisible() || backgroundPlaybackReady) engine.resume() else focusRequester.abandon()
                }
                override fun pause() = engine.pause()
            }
        )
        if (engine.needsAudio) audioRoute.start()
    }

    fun current(): SessionEngine? = _session.value?.engine

    /**
     * Wołane, gdy ekran nauki jest widoczny — Android pozwala wtedy wystartować
     * serwis pierwszoplanowy, który przejmie sesję po zejściu aplikacji w tło.
     * Przebieg [engine] musi już trwać (serwis zamyka się na zakończonej sesji);
     * ponowne wywołanie dla tego samego silnika (nowa próba) uruchamia serwis znowu.
     * Tryb bez pracy w tle nie uruchamia serwisu.
     *
     * Rejestracja tego samego silnika i nazwy talii nie zmienia [session] (równość danych) —
     * serwis, który jeszcze żyje, dalej obserwuje ten sam przebieg, a ten, który zamknął się
     * na końcu poprzedniej próby, jest uruchamiany od nowa i odczytuje sesję w onCreate.
     *
     * Zwraca false, gdy serwisu nie dało się uruchomić (aplikacja zdążyła zejść w tło):
     * sesja nie może wtedy iść "bez rąk".
     */
    fun register(engine: SessionEngine, deckName: String, runsInBackground: Boolean): Boolean {
        if (current() !== engine) prepare(engine, deckName)
        if (audioJob?.isActive != true) {
            audioJob = scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.LAZY) {
                combine(engine.state, engine.inBackground, engine.isPaused) { state, background, paused ->
                    state to sessionAudioIdle(state, background, paused)
                }.collect { (state, idle) ->
                    if (state.status == SessionStatus.FINISHED) {
                        _boundary.value = _boundary.value.copy(ended = true)
                        focus?.release()
                        audioRoute.stop()
                    } else if (engine.needsAudio) {
                        if (idle) focus?.onSessionPaused() else acquireAudioFocus()
                    }
                }
            }.also { it.start() }
        }
        if (!runsInBackground) return true
        return try {
            startService(context, Intent(context, StudySessionService::class.java))
            backgroundPlaybackReady = true
            true
        } catch (e: IllegalStateException) {
            // Android 12+: start serwisu pierwszoplanowego z tła jest zabroniony
            Log.w(TAG, "Cannot start study service", e)
            false
        }
    }

    /** Czy aplikacja jest widoczna (proces na pierwszym planie). */
    fun appVisible(): Boolean = isAppVisible()

    /** Kończy sesję [engine], jeśli nadal jest aktywna. */
    fun release(engine: SessionEngine) {
        if (_session.value?.engine !== engine) return
        audioJob?.cancel()
        audioJob = null
        focus?.release()
        focus = null
        backgroundPlaybackReady = false
        audioRoute.stop()
        engine.stop()
        _boundary.value = _boundary.value.copy(ended = true)
        _session.value = null
    }
}
