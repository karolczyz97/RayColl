package com.raycoll.app.playback

import android.content.Context
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SttResult {
    data class Success(val text: String) : SttResult()
    data object Skipped : SttResult()
    data class Error(val message: String) : SttResult()
    data object PermissionDenied : SttResult()
}

@Singleton
class SttRecognizer @Inject constructor(private val context: Context) {
    suspend fun listen(lang: String, timeoutMs: Long): com.raycoll.app.domain.session.ListenResult {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return com.raycoll.app.domain.session.ListenResult("", com.raycoll.app.domain.session.ListenStatus.ERROR)
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val deferred = CompletableDeferred<com.raycoll.app.domain.session.ListenResult>()
        val accumulator = StringBuilder()
        var partial = ""

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
                val status = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT -> com.raycoll.app.domain.session.ListenStatus.SKIPPED
                    else -> com.raycoll.app.domain.session.ListenStatus.ERROR
                }
                if (deferred.isActive) deferred.complete(com.raycoll.app.domain.session.ListenResult(accumulator.toString(), status))
                recognizer.destroy()
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                if (deferred.isActive) deferred.complete(com.raycoll.app.domain.session.ListenResult(text, com.raycoll.app.domain.session.ListenStatus.COMPLETED))
                recognizer.destroy()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial = partials?.firstOrNull() ?: ""
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        recognizer.startListening(intent)
        return deferred.await()
    }
}
