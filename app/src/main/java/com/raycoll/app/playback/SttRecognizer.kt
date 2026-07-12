package com.raycoll.app.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface SttResult {
    data class Success(val text: String, val confidence: Int) : SttResult
    data object NoSpeech : SttResult
    data object Timeout : SttResult
    data object Skipped : SttResult
    data class Error(val message: String) : SttResult
}

@Singleton
class SttRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accumulator = SttTranscriptAccumulator()
    private val inactivityMs = 2000L
    private val hardTimeoutMs = 15000L

    suspend fun listen(lang: String, timeoutMs: Long = hardTimeoutMs): SttResult {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return SttResult.Error("Speech recognition not available")
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        accumulator.clear()
        var timerJob: Job? = null
        var handled = false

        return suspendCancellableCoroutine { cont ->
            val finish: (SttResult) -> Unit = { r ->
                if (!handled) { handled = true; cont.resume(r) }
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    timerJob?.cancel()
                }
                override fun onRmsChanged(dB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    timerJob?.cancel()
                    timerJob = kotlinx.coroutines.GlobalScope.launch {
                        delay(inactivityMs)
                        finish(SttResult.Timeout)
                    }
                }
                override fun onError(error: Int) {
                    val result = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttResult.NoSpeech
                        else -> SttResult.Error("STT error: $error")
                    }
                    finish(result)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) ?: FloatArray(matches.size)
                    if (matches.isNotEmpty()) {
                        accumulator.addFinal(matches[0])
                        finish(SttResult.Success(accumulator.fullText, (scores.getOrElse(0) { 0f } * 100).toInt()))
                    }
                }
                override fun onPartialResults(partial: Bundle?) {
                    val parts = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    if (parts.isNotEmpty()) accumulator.addInterim(parts[0])
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.replace('-', '_'))
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            recognizer.startListening(intent)

            timerJob = kotlinx.coroutines.GlobalScope.launch {
                delay(timeoutMs)
                finish(SttResult.Timeout)
            }

            cont.invokeOnCancellation {
                timerJob?.cancel()
                recognizer.stopListening()
                recognizer.destroy()
            }
        }
    }
}
