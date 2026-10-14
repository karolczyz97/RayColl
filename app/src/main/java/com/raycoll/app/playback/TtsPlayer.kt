package com.raycoll.app.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.raycoll.app.domain.model.TtsRate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface TtsResult {
    data object Success : TtsResult
    data class Error(val reason: String) : TtsResult
    data class MissingVoice(val language: String) : TtsResult
}

private const val INIT_TIMEOUT_MS = 10_000L
private const val UTTERANCE_TIMEOUT_BASE_MS = 10_000L
private const val UTTERANCE_TIMEOUT_PER_CHAR_MS = 150f

/** Systemowy syntezator mowy. Silnik, który nie wystartował, jest tworzony od nowa przy następnej wypowiedzi. */
@Singleton
class TtsPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private val engineLock = Mutex()
    private var engine: TextToSpeech? = null

    suspend fun speak(text: String, language: String, rate: Float): TtsResult {
        // nie ma czego czytać: bez inicjalizacji silnika i bez czekania na jego callback
        if (text.isBlank()) return TtsResult.Success
        val tts = readyEngine() ?: return TtsResult.Error("TTS init failed")
        val locale = Locale.forLanguageTag(language)
        // brak głosu = język niewspierany albo dane głosu niepobrane — w obu przypadkach proponujemy instalację
        val availability = tts.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED || availability == TextToSpeech.LANG_MISSING_DATA) {
            return TtsResult.MissingVoice(language)
        }
        tts.language = locale
        tts.setSpeechRate(TtsRate.clamp(rate))
        // silnik nie zawsze zgłasza koniec (aktualizacja, utrata fokusu audio) — bez limitu sesja w tle stałaby w miejscu
        var result: TtsResult = TtsResult.Success
        for (chunk in speechChunks(text, TextToSpeech.getMaxSpeechInputLength())) {
            result = withTimeoutOrNull(utteranceTimeoutMs(chunk, rate)) { awaitUtterance(tts, chunk) }
                ?: TtsResult.Error("TTS timeout")
            if (result != TtsResult.Success) break
        }
        if (result is TtsResult.Error) {
            engineLock.withLock {
                if (engine === tts) {
                    engine = null
                    tts.shutdown()
                }
            }
        }
        return result
    }

    private suspend fun readyEngine(): TextToSpeech? = engineLock.withLock {
        engine?.let { return@withLock it }
        val initialized = CompletableDeferred<Boolean>()
        val created = TextToSpeech(context) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }
        try {
            val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { initialized.await() } ?: false
            if (ok) {
                engine = created
                created
            } else {
                null
            }
        } finally {
            if (engine !== created) created.shutdown()
        }
    }

    private suspend fun awaitUtterance(tts: TextToSpeech, text: String): TtsResult =
        suspendCancellableCoroutine { cont ->
            val utteranceId = "raycoll-" + System.nanoTime()

            // filtr po id: spóźniony callback przerwanej wcześniej wypowiedzi nie kończy bieżącej
            fun finish(id: String?, result: TtsResult) {
                if (id == utteranceId && cont.isActive) cont.resume(result)
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) = finish(id, TtsResult.Success)

                @Deprecated("Wymagane przez starsze wersje Androida")
                override fun onError(id: String?) = finish(id, TtsResult.Error("TTS error"))

                override fun onError(id: String?, errorCode: Int) = finish(id, TtsResult.Error("TTS error $errorCode"))

                // przerwanie z zewnątrz kończy wypowiedź — pauzę przy utracie fokusu robi AudioFocusManager
                override fun onStop(id: String?, interrupted: Boolean) = finish(id, TtsResult.Success)
            })
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
                finish(utteranceId, TtsResult.Error("TTS rejected utterance"))
            }
            cont.invokeOnCancellation {
                tts.stop()
                tts.setOnUtteranceProgressListener(null)
            }
        }

    private fun utteranceTimeoutMs(text: String, rate: Float): Long =
        UTTERANCE_TIMEOUT_BASE_MS + (text.length * UTTERANCE_TIMEOUT_PER_CHAR_MS / TtsRate.clamp(rate)).toLong()
}

/** Granice zdań, potem słów; bardzo długie słowo dzielimy bez rozcinania pary surogatów. */
internal fun speechChunks(text: String, maxLength: Int): List<String> {
    require(maxLength >= 2)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + maxLength, text.length)
        if (end < text.length) {
            val sentenceEnd = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', '。', '！', '？'), end - 1)
            val wordEnd = text.lastIndexOf(' ', end - 1)
            end = when {
                sentenceEnd >= start -> sentenceEnd + 1
                wordEnd > start -> wordEnd + 1
                else -> end
            }
            if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}
