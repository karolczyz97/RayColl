package com.raycoll.app.playback

import android.util.Log
import com.raycoll.app.data.repository.PendingReview
import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SpeakResult
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionDeps"

/** Łączy silnik sesji z mową, dźwiękami i zapisem ocen. */
@Singleton
class SessionDepsAdapter @Inject constructor(
    private val ttsPlayer: TtsPlayer,
    private val sttRecognizer: SttRecognizer,
    private val hapticPlayer: HapticAndSoundPlayer,
    private val reviewWrites: ReviewWriteQueue
) : SessionDeps {

    // mikrofon nie może słuchać, gdy aplikacja jeszcze mówi (nagrałby sam siebie) — i odwrotnie
    private val audioTurn = AudioTurn()

    override suspend fun speak(text: String, lang: String, rate: Float): SpeakResult =
        audioTurn.withTurn { ttsPlayer.speak(text, lang, rate).toSpeakResult() }

    private fun TtsResult.toSpeakResult(): SpeakResult = when (this) {
        TtsResult.Success -> SpeakResult.SUCCESS

        is TtsResult.MissingVoice -> {
            Log.w(TAG, "Missing voice: $language")
            SpeakResult.MISSING_VOICE
        }

        is TtsResult.Error -> {
            Log.w(TAG, reason)
            SpeakResult.ERROR
        }
    }

    override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult =
        audioTurn.withTurn { sttRecognizer.listen(lang, timeoutMs, onPartial) }

    override fun feedbackSuccess() = hapticPlayer.playSuccess()

    override fun feedbackError() = hapticPlayer.playError()

    override fun haptic(rating: Int) = hapticPlayer.haptic(rating)

    /** Zapis poza zakresem ekranu (kolejka z potwierdzeniem): wyjście z sesji zaraz po ocenie jej nie gubi. */
    override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) =
        reviewWrites.submit(PendingReview(deckId, cardId, rating, replaceFromBase))
}
