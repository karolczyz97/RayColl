package com.raycoll.app.playback

import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SttResult as DomainSttResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionDepsAdapter @Inject constructor(
    private val ttsPlayer: TtsPlayer,
    private val sttRecognizer: SttRecognizer,
    private val feedback: FeedbackSoundPlayer,
    private val haptics: Haptics,
) : SessionDeps {
    override suspend fun ttsSpeak(text: String, language: String, rate: Float) {
        ttsPlayer.speak(text, language, rate)
    }

    override suspend fun sttListen(language: String, timeoutMs: Long): DomainSttResult {
        return when (val r = sttRecognizer.listen(language, timeoutMs)) {
            is SttResult.Success -> DomainSttResult(r.text, r.confidence)
            SttResult.NoSpeech -> DomainSttResult(error = "no_speech")
            SttResult.Timeout -> DomainSttResult(error = "timeout")
            SttResult.Skipped -> DomainSttResult(skipped = true)
            is SttResult.Error -> DomainSttResult(error = r.message)
        }
    }

    override fun feedbackSuccess() = feedback.playSuccess()
    override fun feedbackError() = feedback.playError()
    override fun haptic(strength: Int) = haptics.rating(strength)
}
