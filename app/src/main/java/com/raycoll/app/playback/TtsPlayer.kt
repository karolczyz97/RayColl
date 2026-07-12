package com.raycoll.app.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class TtsResult {
    data object Success : TtsResult()
    data class Error(val message: String) : TtsResult()
    data class MissingVoice(val lang: String) : TtsResult()
}

@Singleton
class TtsPlayer @Inject constructor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var initialized = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context) { status ->
            initialized.complete(status == TextToSpeech.SUCCESS)
        }
    }

    suspend fun speak(text: String, lang: String, rate: Float): TtsResult {
        if (!initialized.await()) return TtsResult.Error("TTS init failed")
        val tts = this.tts ?: return TtsResult.Error("TTS not available")
        val locale = Locale.forLanguageTag(lang)
        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) {
            return TtsResult.MissingVoice(lang)
        }
        tts.language = locale
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        return suspendCancellableCoroutine { cont ->
            val utteranceId = System.currentTimeMillis().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(TtsResult.Success) }
                override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(TtsResult.Error("TTS error")) }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            cont.invokeOnCancellation { tts.stop(); tts.setOnUtteranceProgressListener(null) }
        }
    }

    fun stop() { tts?.stop() }

    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }
}
