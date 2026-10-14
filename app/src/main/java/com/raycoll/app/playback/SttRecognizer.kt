package com.raycoll.app.playback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class SttRecognizer @Inject constructor(@ApplicationContext private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Jednorazowy nasłuch z twardym limitem czasu; anulowanie (tapnięcie) od razu
     * niszczy rozpoznawanie. Cisza to nie błąd techniczny — wynik COMPLETED z tym,
     * co usłyszano.
     */
    suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return ListenResult("", ListenStatus.ERROR)
        }
        // SpeechRecognizer działa tylko na wątku głównym
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return@withContext ListenResult("", ListenStatus.ERROR)
            }
            val accumulator = SttTranscriptAccumulator()
            try {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine { cont ->
                        val recognizer = try {
                            SpeechRecognizer.createSpeechRecognizer(context)
                        } catch (_: Exception) {
                            if (cont.isActive) cont.resume(ListenResult("", ListenStatus.ERROR))
                            return@suspendCancellableCoroutine
                        }
                        if (recognizer == null) {
                            if (cont.isActive) cont.resume(ListenResult("", ListenStatus.ERROR))
                            return@suspendCancellableCoroutine
                        }

                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        }

                        val isCleanedUp = AtomicBoolean(false)
                        val safeCleanup: () -> Unit = {
                            if (isCleanedUp.compareAndSet(false, true)) {
                                val run = {
                                    try {
                                        recognizer.cancel()
                                    } catch (_: Exception) {}
                                    try {
                                        recognizer.destroy()
                                    } catch (_: Exception) {}
                                }
                                if (Looper.myLooper() == Looper.getMainLooper()) {
                                    run()
                                } else {
                                    mainHandler.post(run)
                                }
                            }
                        }

                        recognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                safeCleanup()
                                if (!cont.isActive) return

                                // razem z niedokończonym fragmentem — recognizer mógł nie zdążyć go zamknąć
                                val text = accumulator.combined()
                                val result = when {
                                    text.isNotBlank() ->
                                        ListenResult(text, ListenStatus.COMPLETED)

                                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                        ListenResult("", ListenStatus.COMPLETED)

                                    else ->
                                        ListenResult("", ListenStatus.ERROR)
                                }
                                cont.resume(result)
                            }
                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                matches?.firstOrNull()?.let { accumulator.addFinal(it) }
                                safeCleanup()
                                if (cont.isActive) {
                                    cont.resume(ListenResult(accumulator.combined(), ListenStatus.COMPLETED))
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                if (isCleanedUp.get() || !cont.isActive) return
                                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = partials?.firstOrNull() ?: return
                                if (text.isNotBlank()) {
                                    accumulator.addPartial(text)
                                    onPartial(accumulator.combined())
                                }
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })

                        cont.invokeOnCancellation {
                            safeCleanup()
                        }

                        try {
                            recognizer.startListening(intent)
                        } catch (_: Exception) {
                            safeCleanup()
                            if (cont.isActive) cont.resume(ListenResult("", ListenStatus.ERROR))
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // twardy timeout: oddaj to, co usłyszano do tej pory
                ListenResult(accumulator.combined(), ListenStatus.COMPLETED)
            }
        }
    }
}
