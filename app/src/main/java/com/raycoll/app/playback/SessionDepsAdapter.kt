package com.raycoll.app.playback

import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.ListenResult as DomainListenResult
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SpeakResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionDepsAdapter @Inject constructor(
    private val ttsPlayer: TtsPlayer,
    private val sttRecognizer: SttRecognizer,
    private val hapticPlayer: HapticAndSoundPlayer,
    private val deckRepository: DeckRepository,
    private val appScope: CoroutineScope,
) : SessionDeps {

    private var isSpeaking = CompletableDeferred<Boolean>().apply { complete(true) }

    override suspend fun speak(text: String, lang: String, rate: Float): SpeakResult {
        // INTERLOCK: STT nie może słuchać podczas TTS
        isSpeaking = CompletableDeferred()
        try {
            return when (ttsPlayer.speak(text, lang, rate)) {
                is TtsResult.Success -> SpeakResult.SUCCESS
                is TtsResult.MissingVoice -> SpeakResult.MISSING_VOICE
                is TtsResult.Error -> SpeakResult.ERROR
            }
        } finally {
            isSpeaking.complete(true)
        }
    }

    override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): DomainListenResult {
        // INTERLOCK: wait for TTS to finish before starting STT
        isSpeaking.await()
        return sttRecognizer.listen(lang, timeoutMs, onPartial)
    }

    override fun feedbackSuccess() { hapticPlayer.playSuccess() }
    override fun feedbackError() { hapticPlayer.playError() }
    override fun haptic(rating: Int) { hapticPlayer.haptic(rating) }

    override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {
        appScope.launch {
            if (replaceFromBase != null) {
                // nadpisanie ponownej oceny: SRS od stanu bazowego, heatmapa bez drugiego +1
                deckRepository.reviewCardAgain(deckId, cardId, rating, replaceFromBase)
            } else {
                deckRepository.reviewCard(deckId, cardId, rating)
            }
        }
    }
}
