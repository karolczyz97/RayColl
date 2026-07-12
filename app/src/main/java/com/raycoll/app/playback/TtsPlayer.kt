package com.raycoll.app.playback

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface TtsResult {
    data object Completed : TtsResult
    data object Cancelled : TtsResult
    data class Error(val cause: String) : TtsResult
    data class MissingVoice(val lang: String) : TtsResult
}

@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var initError: String? = null
    private var currentUtteranceId = AtomicInteger(0)

    init {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) initError = "TTS init failed: $status"
        }
    }

    fun isLanguageAvailable(lang: String): Boolean {
        val locale = toLocale(lang)
        val result = tts?.isLanguageAvailable(locale) ?: return false
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun speak(text: String, lang: String, rate: Float): TtsResult {
        val engine = tts ?: return TtsResult.Error(initError ?: "TTS not initialized")
        if (!isLanguageAvailable(lang)) return TtsResult.MissingVoice(lang)

        val locale = toLocale(lang)
        engine.setLanguage(locale)
        engine.setSpeechRate(rate)

        val utteranceId = currentUtteranceId.incrementAndGet().toString()

        return suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) cont.resume(TtsResult.Completed)
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) cont.resume(TtsResult.Error("TTS error"))
                }
            })

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                cont.resume(TtsResult.Error("TTS speak failed"))
            }

            cont.invokeOnCancellation {
                engine.stop()
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    fun openTtsSettingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    private fun toLocale(lang: String): Locale = when {
        lang.contains('-') -> {
            val parts = lang.split('-')
            Locale(parts[0], parts[1])
        }
        else -> Locale(lang)
    }
}
