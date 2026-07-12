package com.raycoll.app.playback

import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.ListenResult as DomainListenResult
import com.raycoll.app.domain.session.ListenStatus
import com.raycoll.app.domain.session.SessionDeps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionDepsAdapter @Inject constructor(
    private val ttsPlayer: TtsPlayer,
    private val sttRecognizer: SttRecognizer,
    private val hapticPlayer: HapticAndSoundPlayer,
) : SessionDeps {

    private var isSpeaking = CompletableDeferred<Boolean>()

    override suspend fun speak(text: String, lang: String, rate: Float) {
        // Cancel any pending STT — micro cannot listen while TTS speaks
        isSpeaking = CompletableDeferred()
        ttsPlayer.speak(text, lang, rate)
        isSpeaking.complete(true)
    }

    override suspend fun listen(lang: String, timeoutMs: Long): DomainListenResult {
        // INTERLOCK: wait for TTS to finish before starting STT
        isSpeaking.await()
        return sttRecognizer.listen(lang, timeoutMs)
    }

    override fun feedbackSuccess() { hapticPlayer.playSuccess() }
    override fun feedbackError() { hapticPlayer.playError() }
    override fun haptic(rating: Int) { hapticPlayer.haptic(rating) }

    override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {
        // Will be called by SessionEngine; actual repository write happens in StudyViewModel
        // which observes state changes and writes via deckRepository.reviewCard
    }
}
