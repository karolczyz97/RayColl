package com.raycoll.app.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAudioFocusTest {

    private class FakeFocus(var grant: Boolean = true) : AudioFocusRequester {
        var held = false
        var listener: ((FocusChange) -> Unit)? = null
        var requests = 0

        override fun request(onChange: (FocusChange) -> Unit): Boolean {
            requests++
            if (held) return true
            if (!grant) return false
            held = true
            listener = onChange
            return true
        }

        override fun abandon() {
            held = false
            listener = null
        }

        fun change(change: FocusChange) = listener?.invoke(change)
    }

    private class FakeSession : FocusedSession {
        val calls = mutableListOf<String>()
        override fun resume() {
            calls += "resume"
        }
        override fun pause() {
            calls += "pause"
        }
    }

    private val focus = FakeFocus()
    private val session = FakeSession()
    private val audio = SessionAudioFocus(focus, session)

    @Test
    fun `manual pause gives the focus back so other apps are not ducked`() {
        audio.onSessionPlaying()
        assertTrue(focus.held)
        audio.pause()
        audio.onSessionPaused()
        assertFalse(focus.held)
        assertEquals(listOf("pause"), session.calls)
    }

    @Test
    fun `resume asks for focus again and does not start when it is denied`() {
        audio.onSessionPlaying()
        audio.pause()
        focus.grant = false
        assertFalse(audio.play())
        assertEquals("bez fokusu sesja nie rusza", listOf("pause"), session.calls)
        focus.grant = true
        assertTrue(audio.play())
        assertTrue(focus.held)
        assertEquals(listOf("pause", "resume"), session.calls)
    }

    @Test
    fun `transient loss (call, navigation) pauses and resumes when the focus comes back`() {
        audio.onSessionPlaying()
        focus.change(FocusChange.LOST_TRANSIENT)
        audio.onSessionPaused() // serwis widzi pauzę sesji
        assertTrue("czekamy na powrót fokusu", focus.held)
        focus.change(FocusChange.GAINED)
        assertEquals(listOf("pause", "resume"), session.calls)
    }

    @Test
    fun `focus coming back never resumes a session the user paused`() {
        audio.onSessionPlaying()
        focus.change(FocusChange.LOST_TRANSIENT)
        audio.pause() // użytkownik zatrzymał w trakcie rozmowy
        focus.change(FocusChange.GAINED)
        assertEquals(listOf("pause", "pause"), session.calls)
        assertFalse(focus.held)
    }

    @Test
    fun `permanent loss (other app plays music) pauses without automatic resume`() {
        audio.onSessionPlaying()
        focus.change(FocusChange.LOST)
        assertFalse(focus.held)
        assertFalse(audio.pausedByFocusLoss)
        assertEquals(listOf("pause"), session.calls)
    }

    @Test
    fun `session that stops by itself releases the focus`() {
        audio.onSessionPlaying()
        audio.onSessionPaused()
        assertFalse(focus.held)
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun `denied focus at start keeps the session paused`() {
        focus.grant = false
        audio.onSessionPlaying()
        assertEquals(listOf("pause"), session.calls)
    }

    @Test
    fun `recognition owns focus without observer taking it back and next speech reacquires`() = runTest {
        audio.onSessionPlaying()
        audio.withRecognitionFocus {
            assertFalse(focus.held)
            val requests = focus.requests
            repeat(3) { assertTrue(audio.onSessionPlaying()) }
            assertEquals(requests, focus.requests)
            focus.change(FocusChange.LOST_TRANSIENT)
            assertTrue(session.calls.isEmpty())
        }
        assertTrue(audio.onSessionPlaying())
        assertTrue(focus.held)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelled recognition releases delegation so resume can own focus again`() = runTest {
        audio.onSessionPlaying()
        val recognition = async { audio.withRecognitionFocus { awaitCancellation() } }
        runCurrent()
        assertFalse(focus.held)
        recognition.cancel()
        runCurrent()
        assertTrue(audio.play())
        assertTrue(focus.held)
        assertEquals(listOf("resume"), session.calls)
    }
}
