package com.raycoll.app.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    @Test
    fun `StartSession resets state`() {
        val state = SessionState(currentCardIndex = 5, status = SessionStatus.FINISHED)
        val result = sessionReducer(state, SessionAction.StartSession)
        assertEquals(0, result.currentCardIndex)
        assertEquals(SessionStatus.IDLE, result.status)
        assertEquals(0, result.failedCardIds.size)
    }

    @Test
    fun `StartSpeaking sets status and audioPageIndex`() {
        val result = sessionReducer(SessionState(), SessionAction.StartSpeaking(2, 1))
        assertEquals(SessionStatus.SPEAKING, result.status)
        assertEquals(1, result.audioPageIndex)
        assertEquals(2, result.currentStepIndex)
    }

    @Test
    fun `UpdatePartialStt ignored when not listening`() {
        val state = SessionState(status = SessionStatus.IDLE)
        val result = sessionReducer(state, SessionAction.UpdatePartialStt("hello"))
        assertEquals("", result.lastAnswerResult.text)
    }

    @Test
    fun `UpdatePartialStt updates text when listening`() {
        val state = SessionState(status = SessionStatus.LISTENING)
        val result = sessionReducer(state, SessionAction.UpdatePartialStt("hello"))
        assertEquals("hello", result.lastAnswerResult.text)
    }

    @Test
    fun `RevealPage clears peek if same page`() {
        val state = SessionState(peekedPageIndex = 2, revealedPages = listOf(0))
        val result = sessionReducer(state, SessionAction.RevealPage(1, 2))
        assertNull(result.peekedPageIndex)
        assertTrue(result.revealedPages.contains(2))
    }

    @Test
    fun `ShowRatings is no-op when cardReviewed`() {
        val state = SessionState(cardReviewed = true)
        val result = sessionReducer(state, SessionAction.ShowRatings)
        assertEquals(SessionStatus.IDLE, result.status)
    }

    @Test
    fun `ShowRatings sets revealed when not reviewed`() {
        val state = SessionState(cardReviewed = false)
        val result = sessionReducer(state, SessionAction.ShowRatings)
        assertEquals(SessionStatus.REVEALED, result.status)
    }

    @Test
    fun `AdvanceCard resets card state`() {
        val state = SessionState(currentCardIndex = 0, revealedPages = listOf(0, 1, 2), peekedPageIndex = 1, cardReviewed = true)
        val result = sessionReducer(state, SessionAction.AdvanceCard(1))
        assertEquals(1, result.currentCardIndex)
        assertEquals(listOf(0), result.revealedPages)
        assertNull(result.peekedPageIndex)
        assertEquals(false, result.cardReviewed)
    }

    @Test
    fun `ClearError clears message but not status`() {
        val state = SessionState(status = SessionStatus.ERROR, errorMsg = "oops")
        val result = sessionReducer(state, SessionAction.ClearError)
        assertNull(result.errorMsg)
        assertEquals(SessionStatus.ERROR, result.status)
    }

    @Test
    fun `CompleteInteractionGate sets pending step`() {
        val state = SessionState(interactionGate = InteractionGate(
            kind = InteractionGateKind.TAP_TO_REVEAL,
            continueStepIndex = 5,
        ))
        val result = sessionReducer(state, SessionAction.CompleteInteractionGate)
        assertEquals(InteractionGateKind.NONE, result.interactionGate.kind)
        assertEquals(5, result.pendingStepIndexToRun)
    }

    @Test
    fun `AddFailedCard adds to set`() {
        val state = SessionState(failedCardIds = setOf("c1"))
        val result = sessionReducer(state, SessionAction.AddFailedCard("c2"))
        assertTrue(result.failedCardIds.contains("c2"))
        assertEquals(2, result.failedCardIds.size)
    }
}
