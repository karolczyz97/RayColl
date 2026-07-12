package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.steps.expandCompound
import com.raycoll.app.domain.steps.expandModeSteps
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionTest {
    private val fakeDeps = object : SessionDeps {
        override suspend fun ttsSpeak(text: String, language: String, rate: Float) {}
        override suspend fun sttListen(language: String, timeoutMs: Long) = SttResult()
        override fun feedbackSuccess() {}
        override fun feedbackError() {}
        override fun haptic(strength: Int) {}
    }

    @Test
    fun `sessionReducer StartSession resets state`() {
        val state = StudySessionState(currentCardIndex = 5, revealedPages = setOf(0, 1))
        val next = sessionReducer(state, SessionAction.StartSession)
        assertEquals(0, next.currentCardIndex)
        assertEquals(0, next.currentStepIndex)
        assertEquals(setOf(0), next.revealedPages)
    }

    @Test
    fun `sessionReducer RevealPages shows all and clears peek`() {
        val state = StudySessionState(peekedPageIndex = 1)
        val next = sessionReducer(state, SessionAction.RevealPages(setOf(0, 1, 2)))
        assertEquals(setOf(0, 1, 2), next.revealedPages)
        assertNull(next.peekedPageIndex)
    }

    @Test
    fun `sessionReducer AdvanceCard resets per-card state`() {
        val state = StudySessionState(currentCardIndex = 0, revealedPages = setOf(0, 1), cardReviewState = CardReviewState.ManuallyRated)
        val next = sessionReducer(state, SessionAction.AdvanceCard(1))
        assertEquals(1, next.currentCardIndex)
        assertEquals(0, next.currentStepIndex)
        assertEquals(setOf(0), next.revealedPages)
        assertEquals(CardReviewState.None, next.cardReviewState)
    }

    @Test
    fun `sessionReducer FinishSession sets finished`() {
        val next = sessionReducer(StudySessionState(), SessionAction.FinishSession)
        assertEquals(SessionStatus.Finished, next.status)
    }

    @Test
    fun `sessionReducer MarkFailed adds to set`() {
        val next = sessionReducer(StudySessionState(), SessionAction.MarkFailed("c1"))
        assertTrue(next.failedCardIds.contains("c1"))
    }

    @Test
    fun `expandModeSteps expands compound to atomic`() {
        val steps = listOf(ShowPage(0), ShowAllPages(), CompoundStep(params = AutoPassNextParams(rating = 3)))
        val expanded = expandModeSteps(steps)
        assertEquals(4, expanded.size) // ShowPage + ShowAllPages + AutoRateFixed + NextCard
        assertTrue(expanded[0] is ShowPage)
        assertTrue(expanded[1] is ShowAllPages)
        assertTrue(expanded[2] is AutoRateFixed)
    }

    @Test
    fun `compound expander listen_grade produces correct steps`() {
        val compound = ListenGradeParams(answerPage = 1, threshold = 70, manualFallback = true)
        val expanded = expandCompound(CompoundStep(params = compound))
        assertTrue(expanded.isNotEmpty())
        assertTrue(expanded.any { it is ListenAndCheck })
    }

    @Test
    fun `compound expander fail_next produces mark_failed and auto_rate_1`() {
        val expanded = expandCompound(CompoundStep(params = FailNextParams()))
        assertTrue(expanded.size >= 2)
        assertTrue(expanded.any { it is MarkFailed })
        assertTrue(expanded.any { it is AutoRateFixed })
    }

    @Test
    fun `StepExecutor waits out steps reach end of list`() = runTest {
        val ctx = SessionContext(
            cards = listOf(Flashcard(id = "c1", pages = listOf("a", "b"))),
            steps = listOf(Wait(ms = 10)),
            group = FlashcardGroup(id = "g1", name = "Test", activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en")),
            deps = fakeDeps,
            baseSrsStates = emptyMap(),
        )
        val state = StudySessionState()
        val actions = StepExecutor.executeStep(state, ctx)
        assertTrue(actions.any { it is SessionAction.SetCurrentStep })
    }

    @Test
    fun `StepExecutor show_page reveals page`() = runTest {
        val ctx = SessionContext(
            cards = listOf(Flashcard(id = "c1", pages = listOf("a", "b"))),
            steps = listOf(ShowPage(0)),
            group = FlashcardGroup(id = "g1", name = "Test", activeModeId = "c", pageNames = listOf("a", "b"), pageLanguages = listOf("en", "en")),
            deps = fakeDeps,
            baseSrsStates = emptyMap(),
        )
        val actions = StepExecutor.executeStep(StudySessionState(), ctx)
        assertTrue(actions.any { it is SessionAction.RevealPage })
    }

    @Test
    fun `StepExecutor show_ratings when unrevealed sets revealed status`() = runTest {
        val ctx = SessionContext(
            cards = listOf(Flashcard(id = "c1", pages = listOf("a"))),
            steps = listOf(ShowRatings()),
            group = FlashcardGroup(id = "g1", name = "Test", activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en")),
            deps = fakeDeps,
            baseSrsStates = emptyMap(),
        )
        val actions = StepExecutor.executeStep(StudySessionState(), ctx)
        assertTrue(actions.any { it is SessionAction.ShowRatings })
    }

    @Test
    fun `StepExecutor condition incorrect skips correct-only steps`() = runTest {
        val ctx = SessionContext(
            cards = listOf(Flashcard(id = "c1", pages = listOf("a"))),
            steps = listOf(FeedbackSuccess(condition = StepCondition.correct), FeedbackError(condition = StepCondition.wrong)),
            group = FlashcardGroup(id = "g1", name = "Test", activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en")),
            deps = fakeDeps,
            baseSrsStates = emptyMap(),
        )
        val state = StudySessionState()
        val actions = StepExecutor.executeStep(state, ctx)
        assertTrue(actions.any { it is SessionAction.SetCurrentStep })
    }
}
