package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    @Test
    fun `StartSession resets state`() {
        val state = SessionState(
            currentCardIndex = 5,
            status = SessionStatus.FINISHED,
            audioPageIndex = 1,
            failedCardIds = setOf("x")
        )
        val result = sessionReducer(state, SessionAction.StartSession(emptyList()))
        assertEquals(0, result.currentCardIndex)
        assertEquals(SessionStatus.IDLE, result.status)
        assertEquals(0, result.failedCardIds.size)
        assertNull(result.audioPageIndex)
    }

    @Test
    fun `StartSession carries the cards and nothing else from the previous state`() {
        val card = Flashcard(id = "c1", pages = listOf("a", "b"), srsState = SrsState())
        val result = sessionReducer(SessionState(currentCardIndex = 3), SessionAction.StartSession(listOf(card)))
        assertEquals(SessionState(cards = listOf(card)), result)
    }

    @Test
    fun `FinishSession clears audio page and error`() {
        val state = SessionState(audioPageIndex = 1, error = SessionError.TTS, status = SessionStatus.SPEAKING)
        val result = sessionReducer(state, SessionAction.FinishSession)
        assertEquals(SessionStatus.FINISHED, result.status)
        assertNull(result.audioPageIndex)
        assertNull(result.error)
    }

    @Test
    fun `StartSpeaking sets status and audioPageIndex`() {
        val result = sessionReducer(SessionState(), SessionAction.StartSpeaking(1))
        assertEquals(SessionStatus.SPEAKING, result.status)
        assertEquals(1, result.audioPageIndex)
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
    fun `RevealPages clears peek if the page is revealed`() {
        val state = SessionState(peekedPageIndex = 2, revealedPages = listOf(0))
        val result = sessionReducer(state, SessionAction.RevealPages(listOf(2)))
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
        val state =
            SessionState(
                currentCardIndex = 0,
                revealedPages = listOf(0, 1, 2),
                peekedPageIndex = 1,
                cardReviewed = true
            )
        val result = sessionReducer(state, SessionAction.AdvanceCard(1))
        assertEquals(1, result.currentCardIndex)
        assertEquals(listOf(0), result.revealedPages)
        assertNull(result.peekedPageIndex)
        assertEquals(false, result.cardReviewed)
    }

    @Test
    fun `AdvanceCard starts the new card without a result but keeps the old one for the exit animation`() {
        val answer = LastAnswerResult(status = AnswerStatus.CORRECT, percent = 92, suggestedRating = 4)
        val state = SessionState(currentCardIndex = 0, lastAnswerResult = answer)
        val result = sessionReducer(state, SessionAction.AdvanceCard(1))
        // silnik i warunki kroków widzą czystą kartę
        assertEquals(LastAnswerResult(), result.lastAnswerResult)
        // znikający panel ma co pokazać (płynne przejście bez mignięcia pustego panelu)
        assertEquals(answer, result.previousAnswerResult)

        val listening = sessionReducer(result, SessionAction.StartListening(1))
        assertEquals(answer, listening.previousAnswerResult)
        assertEquals(LastAnswerResult(), sessionReducer(result, SessionAction.FinishSession).previousAnswerResult)
        val restarted = sessionReducer(result, SessionAction.StartSession(emptyList()))
        assertEquals(LastAnswerResult(), restarted.previousAnswerResult)
    }

    @Test
    fun `action phase transitions clear lastAnswerResult but ShowRatings preserves it for manual grading`() {
        val answer = LastAnswerResult(status = AnswerStatus.CORRECT, percent = 92)
        val state = SessionState(lastAnswerResult = answer)
        val listening = sessionReducer(state, SessionAction.StartListening(1))
        assertEquals(AnswerStatus.NONE, listening.lastAnswerResult.status)

        val gate = sessionReducer(state, SessionAction.StartTapRevealGate(1, TapRevealMode.SINGLE))
        assertEquals(AnswerStatus.NONE, gate.lastAnswerResult.status)

        val ratings = sessionReducer(state, SessionAction.ShowRatings)
        assertEquals(AnswerStatus.CORRECT, ratings.lastAnswerResult.status)
    }

    @Test
    fun `ClearError clears message but not status`() {
        val state = SessionState(status = SessionStatus.ERROR, error = SessionError.TTS)
        val result = sessionReducer(state, SessionAction.ClearError)
        assertNull(result.error)
        assertEquals(SessionStatus.ERROR, result.status)
    }

    @Test
    fun `CompleteInteractionGate sets pending step`() {
        val state = SessionState(
            interactionGate = InteractionGate(
                kind = InteractionGateKind.TAP_TO_REVEAL,
                continueStepIndex = 5
            )
        )
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
