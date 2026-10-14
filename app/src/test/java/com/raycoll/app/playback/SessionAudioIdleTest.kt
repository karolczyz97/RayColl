package com.raycoll.app.playback

import com.raycoll.app.domain.session.InteractionGate
import com.raycoll.app.domain.session.InteractionGateKind
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiedy serwis oddaje fokus audio i wyłącza ciszę (B12 #13). */
class SessionAudioIdleTest {

    private val waitingForTap = SessionState(
        status = SessionStatus.IDLE,
        interactionGate = InteractionGate(kind = InteractionGateKind.TAP_TO_REVEAL)
    )

    @Test
    fun `tap gate in foreground releases audio`() {
        assertTrue(sessionAudioIdle(waitingForTap, inBackground = false, manuallyPaused = false))
    }

    @Test
    fun `tap gate in background keeps playing hands free`() {
        assertFalse(sessionAudioIdle(waitingForTap, inBackground = true, manuallyPaused = false))
    }

    @Test
    fun `speaking session holds audio, pause releases it`() {
        val speaking = SessionState(status = SessionStatus.SPEAKING)
        assertFalse(sessionAudioIdle(speaking, inBackground = true, manuallyPaused = false))
        assertTrue(sessionAudioIdle(speaking, inBackground = true, manuallyPaused = true))
    }

    @Test
    fun `revealed ratings and errors wait for the user even in background`() {
        assertTrue(sessionAudioIdle(SessionState(status = SessionStatus.REVEALED), true, false))
        assertTrue(sessionAudioIdle(SessionState(status = SessionStatus.ERROR), true, false))
    }

    @Test
    fun `notification shows progress while playing and the paused text when idle`() {
        val state = SessionState(status = SessionStatus.SPEAKING, currentCardIndex = 1)
        val playing = sessionPlayback(state, "Deck", idle = false, pausedText = "paused")
        assertEquals("2/0", playing.progress)
        assertTrue(playing.playing)
        val idle = sessionPlayback(state, "Deck", idle = true, pausedText = "paused")
        assertEquals("paused", idle.progress)
        assertFalse(idle.playing)
        assertEquals("Deck", idle.deckName)
    }
}
