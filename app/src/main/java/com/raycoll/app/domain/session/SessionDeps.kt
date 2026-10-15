package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.SrsState

/** Świat zewnętrzny silnika sesji: mowa, dźwięki, haptyka i zapis ocen. */
interface SessionDeps {
    /** Odmowa fokusu wstrzymuje sesję przed rozpoczęciem mowy lub nasłuchu. */
    fun acquireAudioFocus(): Boolean = true

    suspend fun speak(text: String, lang: String, rate: Float): SpeakResult
    suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult
    fun feedbackSuccess()
    fun feedbackError()
    fun haptic(rating: Int)

    /** [replaceFromBase] != null: ponowna ocena tej samej karty, liczona od stanu sprzed pierwszej. */
    fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?)
}

enum class SpeakResult { SUCCESS, ERROR, MISSING_VOICE }

data class ListenResult(val text: String, val status: ListenStatus)

enum class ListenStatus { COMPLETED, ERROR }
