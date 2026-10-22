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
import android.util.Log
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SttRecognizer"

/**
 * Wynik nasłuchu zakończonego błędem rozpoznawania: usłyszany tekst ma pierwszeństwo,
 * cisza (brak dopasowania / brak mowy) to pusty, poprawny wynik, reszta to błąd techniczny.
 */
internal fun listenResultForError(error: Int, heard: String): ListenResult = when {
    heard.isNotBlank() -> ListenResult(heard, ListenStatus.COMPLETED)

    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        ListenResult("", ListenStatus.COMPLETED)

    else -> ListenResult("", ListenStatus.ERROR)
}

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
            Log.w(TAG, "No microphone permission")
            return ListenResult("", ListenStatus.ERROR)
        }
        // SpeechRecognizer działa tylko na wątku głównym
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition unavailable")
                return@withContext ListenResult("", ListenStatus.ERROR)
            }
            val accumulator = SttTranscriptAccumulator()
            // twardy timeout: oddaj to, co usłyszano do tej pory
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<ListenResult> { cont ->
                    val recognizer = try {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot create speech recognizer", e)
                        if (cont.isActive) cont.resume(ListenResult("", ListenStatus.ERROR))
                        return@suspendCancellableCoroutine
                    }

                    val isCleanedUp = AtomicBoolean(false)
                    val safeCleanup: () -> Unit = {
                        if (isCleanedUp.compareAndSet(false, true)) {
                            // rozpoznawanie mogło już się samo zamknąć — sprzątanie nie ma czego zgłosić
                            val destroy = {
                                try {
                                    recognizer.cancel()
                                } catch (_: Exception) {}
                                try {
                                    recognizer.destroy()
                                } catch (_: Exception) {}
                            }
                            if (Looper.getMainLooper().isCurrentThread) {
                                destroy()
                            } else {
                                mainHandler.post(destroy)
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
                            val result = listenResultForError(error, accumulator.combined())
                            if (result.status == ListenStatus.ERROR) Log.w(TAG, "Recognition error $error")
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
                        recognizer.startListening(recognizerIntent(lang))
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot start listening", e)
                        safeCleanup()
                        if (cont.isActive) cont.resume(ListenResult("", ListenStatus.ERROR))
                    }
                }
            } ?: ListenResult(accumulator.combined(), ListenStatus.COMPLETED)
        }
    }

    private fun recognizerIntent(lang: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }
}
