package com.raycoll.app.playback

import android.speech.SpeechRecognizer
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SttErrorMappingTest {
    @Test
    fun `heard text wins over any error code`() {
        val expected = ListenResult("hello", ListenStatus.COMPLETED)
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_NETWORK, "hello"))
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_NO_MATCH, "hello"))
    }

    @Test
    fun `silence is an empty completed result`() {
        val expected = ListenResult("", ListenStatus.COMPLETED)
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_NO_MATCH, ""))
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, "  "))
    }

    @Test
    fun `technical failures are errors`() {
        val expected = ListenResult("", ListenStatus.ERROR)
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_NETWORK, ""))
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_AUDIO, ""))
        assertEquals(expected, listenResultForError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ""))
    }
}
