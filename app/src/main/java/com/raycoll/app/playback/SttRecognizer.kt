package com.raycoll.app.playback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SttRecognizer @Inject constructor(private val context: Context) {

    /**
     * Jednorazowy nasłuch. Timeout twardy egzekwowany tutaj; anulowanie z zewnątrz
     * (skip tapem) niszczy recognizer natychmiast. Brak mowy (NO_MATCH/SPEECH_TIMEOUT)
     * to NIE błąd techniczny — wynik COMPLETED z tym, co usłyszano (sekcja 6.5).
     */
    suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return ListenResult("", ListenStatus.ERROR)
        }
        // SpeechRecognizer wymaga main thread (pułapka 4)
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return@withContext ListenResult("", ListenStatus.ERROR)
            }
            val accumulator = SttTranscriptAccumulator()
            try {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine { cont ->
                        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        }
                        recognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: android.os.Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                // combined() — nie gub partiali, których recognizer nie zdążył
                                // sfinalizować (parytet z getText() w TS)
                                val result = when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                        ListenResult(accumulator.combined(), ListenStatus.COMPLETED)
                                    else -> ListenResult(accumulator.combined(), ListenStatus.ERROR)
                                }
                                recognizer.destroy()
                                if (cont.isActive) cont.resume(result) {}
                            }
                            override fun onResults(results: android.os.Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                matches?.firstOrNull()?.let { accumulator.addFinal(it) }
                                recognizer.destroy()
                                if (cont.isActive) {
                                    cont.resume(ListenResult(accumulator.current().fullText, ListenStatus.COMPLETED)) {}
                                }
                            }
                            override fun onPartialResults(partialResults: android.os.Bundle?) {
                                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = partials?.firstOrNull() ?: return
                                if (text.isNotBlank()) {
                                    accumulator.addPartial(text)
                                    onPartial(accumulator.combined())
                                }
                            }
                            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                        })
                        cont.invokeOnCancellation {
                            recognizer.cancel()
                            recognizer.destroy()
                        }
                        recognizer.startListening(intent)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // twardy timeout: oddaj to, co usłyszano do tej pory
                ListenResult(accumulator.combined(), ListenStatus.COMPLETED)
            }
        }
    }
}
