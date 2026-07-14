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
        val availability = tts.isLanguageAvailable(locale)
        // brak głosu = język niewspierany LUB dane głosu niepobrane — oba przypadki
        // prowadzą do dialogu z ACTION_INSTALL_TTS_DATA
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED || availability == TextToSpeech.LANG_MISSING_DATA) {
            return TtsResult.MissingVoice(lang)
        }
        tts.language = locale
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        return suspendCancellableCoroutine { cont ->
            val expectedId = "raycoll-" + System.nanoTime()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                // filtr po id — spóźniony callback poprzedniej (przerwanej) wypowiedzi
                // nie może przedwcześnie zakończyć bieżącej
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == expectedId && cont.isActive) cont.resume(TtsResult.Success)
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId == expectedId && cont.isActive) cont.resume(TtsResult.Error("TTS error"))
                }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, expectedId)
            cont.invokeOnCancellation { tts.stop(); tts.setOnUtteranceProgressListener(null) }
        }
    }

    fun stop() { tts?.stop() }

    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }
}
