package com.raycoll.app.playback

/** Sesja widziana przez fokus audio: wznów / wstrzymaj. */
interface FocusedSession {
    fun resume()
    fun pause()
}

/**
 * Kiedy sesja nauki w tle trzyma fokus audio i kiedy wraca po jego odzyskaniu.
 * - Sesja gra → trzyma fokus; pauza (przyciskiem albo sama) → oddaje fokus, więc inne
 *   aplikacje nie są ściszone przez całą pauzę.
 * - Wznowienie przyciskiem prosi o fokus od nowa; odmowa = sesja dalej stoi.
 * - Chwilowa utrata (rozmowa, komunikat nawigacji) → pauza i automatyczne wznowienie,
 *   gdy fokus wróci — tylko gdy to ona zatrzymała sesję, nigdy po pauzie użytkownika.
 * - Trwała utrata (inna aplikacja zaczęła grać muzykę) → pauza bez automatycznego wznowienia.
 */
class SessionAudioFocus(private val focus: AudioFocusRequester, private val session: FocusedSession) {
    private var recognitionOwnsFocus = false

    /** Usługa rozpoznawania sama prosi o EXCLUSIVE; nasz listener nie może wtedy anulować mikrofonu. */
    suspend fun <T> withRecognitionFocus(block: suspend () -> T): T {
        recognitionOwnsFocus = true
        focus.abandon()
        try {
            return block()
        } finally {
            recognitionOwnsFocus = false
        }
    }

    /** Sesja stoi z powodu chwilowej utraty fokusu i wróci sama po jego odzyskaniu. */
    var pausedByFocusLoss: Boolean = false
        private set

    /** Sesja gra (start, wznowienie z aplikacji) — potrzebny fokus; odmowa wstrzymuje sesję. */
    fun onSessionPlaying(): Boolean {
        if (recognitionOwnsFocus) return true
        if (pausedByFocusLoss) return false
        val granted = focus.request(::onFocusChange)
        if (!granted) session.pause()
        return granted
    }

    /** Sesja stoi — fokus oddany, chyba że czekamy na jego powrót po chwilowej utracie. */
    fun onSessionPaused() {
        if (!pausedByFocusLoss) focus.abandon()
    }

    /** Użytkownik chce grać (powiadomienie, słuchawki). False: fokus odmówiony, sesja się nie wznawia. */
    fun play(): Boolean {
        if (pausedByFocusLoss) focus.abandon()
        pausedByFocusLoss = false
        if (!focus.request(::onFocusChange)) return false
        session.resume()
        return true
    }

    /** Świadoma pauza użytkownika: oddaje fokus i nie wraca sama. */
    fun pause() {
        pausedByFocusLoss = false
        session.pause()
        focus.abandon()
    }

    /** Koniec sesji / serwisu. */
    fun release() {
        pausedByFocusLoss = false
        focus.abandon()
    }

    private fun onFocusChange(change: FocusChange) {
        when (change) {
            FocusChange.LOST_TRANSIENT -> {
                pausedByFocusLoss = true
                session.pause()
            }

            FocusChange.LOST -> {
                pausedByFocusLoss = false
                session.pause()
                focus.abandon()
            }

            FocusChange.GAINED -> if (pausedByFocusLoss) {
                pausedByFocusLoss = false
                session.resume()
            }
        }
    }
}
