package com.raycoll.app.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.raycoll.app.domain.session.SessionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Aktywna sesja nauki widziana przez cały proces. */
data class ActiveSession(val engine: SessionEngine, val deckName: String)

/**
 * Jedyny właściciel aktywnej sesji: ekran nauki ją rejestruje, serwis w tle
 * i synchronizacja ją obserwują. Rejestracja sesji, która może iść w tle,
 * uruchamia serwis; zwolnienie sesji zatrzymuje go (serwis kończy się sam).
 */
@Singleton
class StudySessionHolder @Inject constructor(@ApplicationContext private val context: Context) {
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
     */
    fun register(engine: SessionEngine, deckName: String, runsInBackground: Boolean) {
        _session.value?.engine?.takeIf { it !== engine }?.stop()
        _session.value = ActiveSession(engine, deckName)
        if (runsInBackground) {
            ContextCompat.startForegroundService(context, Intent(context, StudySessionService::class.java))
        }
    }

    /** Kończy sesję [engine], jeśli nadal jest aktywna. */
    fun release(engine: SessionEngine) {
        if (_session.value?.engine !== engine) return
        engine.stop()
        _session.value = null
    }
}
