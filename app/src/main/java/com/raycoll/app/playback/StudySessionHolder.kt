package com.raycoll.app.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.raycoll.app.domain.session.SessionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "StudySessionHolder"

/** Aktywna sesja nauki widziana przez cały proces. */
data class ActiveSession(val engine: SessionEngine, val deckName: String)

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
    private val isAppVisible: () -> Boolean
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        { ctx, intent -> ContextCompat.startForegroundService(ctx, intent) },
        { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
    )

    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()
    private var controls: SessionControls? = null

    fun attachControls(controls: SessionControls) {
        this.controls = controls
    }

    fun detachControls(controls: SessionControls) {
        if (this.controls === controls) this.controls = null
    }

    /** Serwis prosi o fokus; tryb bez serwisu wznawia sam silnik. */
    fun resume() {
        val controls = controls
        if (controls != null) controls.play() else current()?.resume()
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
        _session.value?.engine?.takeIf { it !== engine }?.stop()
        _session.value = ActiveSession(engine, deckName)
        if (!runsInBackground) return true
        return try {
            startService(context, Intent(context, StudySessionService::class.java))
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
        engine.stop()
        _session.value = null
    }
}
