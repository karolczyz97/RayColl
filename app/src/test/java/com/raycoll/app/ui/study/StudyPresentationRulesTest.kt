package com.raycoll.app.ui.study

import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Co ekran nauki pokazuje pod kartą i kiedy widać panel odpowiedzi. */
class StudyPresentationRulesTest {
    private val correct = LastAnswerResult(status = AnswerStatus.CORRECT, text = "kot", suggestedRating = 4)

    @Test
    fun `ratings after reveal, disabled once the card is rated, with the suggested rating`() {
        val waiting = SessionState(status = SessionStatus.AWAITING_RATING, lastAnswerResult = correct)
        assertEquals(
            StudyActionState(StudyActionKind.RATINGS, enabled = true, suggested = 4),
            studyActionState(waiting, 2)
        )
        assertFalse(studyActionState(waiting.copy(cardReviewed = true), 2).enabled)
    }

    @Test
    fun `reveal only while pages are hidden, nothing during listening or after an answer`() {
        val fresh = SessionState(revealedPages = listOf(0))
        assertEquals(StudyActionKind.REVEAL, studyActionState(fresh, 2).kind)
        assertEquals(StudyActionKind.NONE, studyActionState(fresh.copy(revealedPages = listOf(0, 1)), 2).kind)
        assertEquals(StudyActionKind.NONE, studyActionState(fresh.copy(status = SessionStatus.LISTENING), 2).kind)
        assertEquals(StudyActionKind.NONE, studyActionState(fresh.copy(lastAnswerResult = correct), 2).kind)
        // strony spoza aktywnych nie liczą się jako odsłonięte
        assertEquals(StudyActionKind.REVEAL, studyActionState(fresh.copy(revealedPages = listOf(0, 3)), 2).kind)
    }

    @Test
    fun `answer panel shows while listening and after an answer, but not while the question is read`() {
        val listening = SessionState(status = SessionStatus.LISTENING, audioPageIndex = 1, revealedPages = listOf(0))
        val panel = answerPanelState(listening)
        assertTrue(panel.visible)
        assertTrue(panel.listening)
        assertTrue(panel.listeningLabel)
        assertFalse(answerPanelState(listening.copy(revealedPages = listOf(0, 1))).listeningLabel)

        assertTrue(answerPanelState(SessionState(lastAnswerResult = correct)).visible)
        val readingQuestion =
            SessionState(status = SessionStatus.SPEAKING, audioPageIndex = 0, lastAnswerResult = correct)
        assertFalse(answerPanelState(readingQuestion).visible)
    }

    @Test
    fun `after the card changes the hiding panel keeps the previous answer`() {
        val next = SessionState(previousAnswerResult = correct)
        val panel = answerPanelState(next)
        assertFalse(panel.visible)
        assertEquals(correct, panel.answer)
    }
}
