package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionNavigationTest {
    private val cards = (1..3).map { Flashcard("c$it", listOf("q$it", "a$it", "example$it"), SrsState()) }
    private val manual = listOf(ShowPageStep(), WaitForTapToRevealStep(), ShowRatingsStep())

    private fun engine(
        scope: CoroutineScope,
        deps: SessionDeps,
        steps: List<ModeStep> = manual,
        clock: FakeClock = FakeClock()
    ) = SessionEngine(
        FlashcardGroup("deck", "Deck", cards = emptyList(), activePageCount = 3),
        steps,
        deps,
        clock,
        1f,
        scope
    ).apply { start(cards) }

    private fun SessionEngine.begin(): CardNavigation {
        val s = state.value
        return requireNotNull(beginNavigation(s.sessionId, s.cards[s.currentCardIndex].id))
    }

    @Test
    fun `returning restores a partially revealed card and its tap gate`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps)
        eng.handleCardPress()
        assertEquals(listOf(0, 1), eng.state.value.revealedPages)
        eng.endNavigation(eng.begin(), CardDestination.Card("c2"))
        eng.endNavigation(eng.begin(), CardDestination.Card("c1"))
        assertEquals(listOf(0, 1), eng.state.value.revealedPages)
        assertEquals(InteractionGateKind.TAP_TO_REVEAL, eng.state.value.interactionGate.kind)
        eng.handleCardPress()
        assertEquals(listOf(0, 1, 2), eng.state.value.revealedPages)
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
        assertTrue(deps.reviews.isEmpty())
        eng.stop()
    }

    @Test
    fun `returning to a rated card keeps its answer and srs without another review`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val eng = engine(this, deps)
            eng.handleCardPress()
            eng.handleCardPress()
            val result = LastAnswerResult(AnswerStatus.CORRECT, text = "recognized", percent = 95, pageIndex = 1)
            eng.dispatch(SessionAction.SetLastAnswerResult(result))
            eng.rate(3)
            val ratedSrs = eng.state.value.cards[0].srsState
            eng.endNavigation(eng.begin(), CardDestination.Card("c1"))
            val restored = eng.state.value
            assertEquals(listOf(0, 1, 2), restored.revealedPages)
            assertEquals(result, restored.lastAnswerResult)
            assertEquals(ratedSrs, restored.cards[0].srsState)
            assertTrue(restored.cardReviewed)
            assertEquals(1, deps.reviews.size)
            eng.rate(4)
            assertEquals(2, deps.reviews.size)
            assertEquals(cards[0].srsState, deps.reviews.last().replaceFromBase)
            assertEquals(ratedSrs.repetitions, eng.state.value.cards[0].srsState.repetitions)
            eng.stop()
        }

    @Test
    fun `cancelled navigation preserves the current card and revealed pages`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps)
        eng.handleCardPress()
        val before = eng.state.value
        eng.endNavigation(eng.begin(), null)
        assertEquals(before, eng.state.value)
        assertTrue(deps.reviews.isEmpty())
        eng.stop()
    }

    @Test
    fun `automatic grading and advancement wait for a drag to finish`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps, listOf(WaitStep(ms = 1_000), AutoRateFixedStep(rating = 3), NextCardStep()))
        val drag = eng.begin()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(0, eng.state.value.currentCardIndex)
        assertTrue(deps.reviews.isEmpty())
        eng.endNavigation(drag, null)
        runCurrent()
        assertEquals(1, eng.state.value.currentCardIndex)
        assertEquals(listOf("c1"), deps.reviews.map { it.cardId })
        eng.stop()
    }

    @Test
    fun `committed drag cancels the old card before its waiting auto grade can run`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val eng = engine(this, deps, listOf(WaitStep(ms = 1_000), AutoRateFixedStep(rating = 3), NextCardStep()))
            val drag = eng.begin()
            advanceTimeBy(1_001)
            runCurrent()
            eng.endNavigation(drag, CardDestination.Card("c2"))
            runCurrent()
            assertEquals(1, eng.state.value.currentCardIndex)
            assertTrue(deps.reviews.isEmpty())
            eng.stop()
        }

    @Test
    fun `queued advancement cannot skip a second card after a gesture committed`() = runTest(StandardTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps, listOf(ShowAllPagesStep(), ShowRatingsStep()))
        runCurrent()
        eng.rate(3)
        val drag = eng.begin()
        eng.endNavigation(drag, CardDestination.Card("c2"))
        runCurrent()
        assertEquals(1, eng.state.value.currentCardIndex)
        assertEquals(1, deps.reviews.size)
        assertEquals(1, eng.reviewedCardCount)
        eng.stop()
    }

    @Test
    fun `manual ratings are ignored while a gesture is in progress`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps, listOf(ShowAllPagesStep(), ShowRatingsStep()))
        val drag = eng.begin()
        eng.rate(3)
        assertTrue(deps.reviews.isEmpty())
        eng.endNavigation(drag, null)
        eng.rate(3)
        assertEquals(1, deps.reviews.size)
        eng.stop()
    }

    @Test
    fun `revisiting an automatically graded card does not rerun its mode`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(
            this,
            deps,
            listOf(ShowAllPagesStep(), AutoRateFixedStep(rating = 3), WaitStep(ms = 10_000), NextCardStep())
        )
        eng.endNavigation(eng.begin(), CardDestination.Card("c2"))
        eng.endNavigation(eng.begin(), CardDestination.Card("c1"))
        assertEquals(0, eng.state.value.currentCardIndex)
        assertEquals(listOf(0, 1, 2), eng.state.value.revealedPages)
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
        assertEquals(2, deps.reviews.size)
        eng.rate(4)
        assertEquals(3, deps.reviews.size)
        assertNotNull(deps.reviews.last().replaceFromBase)
        eng.stop()
    }

    @Test
    fun `stale and repeated gesture completions cannot change the session`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(this, FakeSessionDeps())
        val drag = eng.begin()
        eng.endNavigation(drag, CardDestination.Card("c2"))
        eng.endNavigation(drag, CardDestination.Card("c3"))
        assertEquals(1, eng.state.value.currentCardIndex)
        eng.start(cards)
        val newDrag = eng.begin()
        eng.endNavigation(drag, CardDestination.Card("c2"))
        assertEquals(newDrag, eng.state.value.navigation)
        assertEquals(0, eng.state.value.currentCardIndex)
        eng.endNavigation(newDrag, null)
        assertTrue(eng.state.value.cardHistory.isEmpty())
        eng.stop()
    }

    @Test
    fun `mismatched origins and nonadjacent targets cannot navigate`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(this, FakeSessionDeps())
        assertNull(eng.beginNavigation(eng.state.value.sessionId, "c2"))
        eng.endNavigation(eng.begin(), CardDestination.Card("c3"))
        assertEquals(0, eng.state.value.currentCardIndex)
        assertNull(eng.state.value.navigation)
        eng.endNavigation(eng.begin(), CardDestination.Summary)
        assertFalse(eng.state.value.status == SessionStatus.FINISHED)
        eng.stop()
    }

    @Test
    fun `two overlapping gestures can reach the second neighbor without running the card between`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val eng = engine(this, deps)
            val drag = eng.begin()
            assertTrue(eng.continueNavigation(drag))
            eng.endNavigation(drag, CardDestination.Card("c3"))
            assertEquals(2, eng.state.value.currentCardIndex)
            assertEquals(setOf("c1"), eng.state.value.cardHistory.keys)
            assertTrue(deps.reviews.isEmpty())
            assertFalse(eng.continueNavigation(drag))
            eng.stop()
        }

    @Test
    fun `explicit restart resets the current face but a new session clears all remembered faces`() =
        runTest(UnconfinedTestDispatcher()) {
            val eng = engine(this, FakeSessionDeps())
            eng.handleCardPress()
            eng.endNavigation(eng.begin(), CardDestination.Card("c2"))
            eng.endNavigation(eng.begin(), CardDestination.Card("c1"))
            eng.restartCurrentCard()
            assertEquals(listOf(0), eng.state.value.revealedPages)
            assertFalse(eng.state.value.cardHistory.containsKey("c1"))
            eng.start(cards)
            advanceUntilIdle()
            assertTrue(eng.state.value.cardHistory.isEmpty())
            assertEquals(listOf(0), eng.state.value.revealedPages)
            eng.stop()
        }

    @Test
    fun `summary is committed only after leaving the final real card`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps)
        eng.endNavigation(eng.begin(), CardDestination.Card("c2"))
        eng.endNavigation(eng.begin(), CardDestination.Card("c3"))
        val drag = eng.begin()
        assertFalse(eng.state.value.status == SessionStatus.FINISHED)
        eng.endNavigation(drag, CardDestination.Summary)
        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        assertEquals(3, eng.state.value.cards.size)
        assertTrue(deps.reviews.isEmpty())
    }

    @Test
    fun `background playback releases a gesture even when no ui frames are running`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val eng = engine(this, deps, listOf(WaitStep(ms = 1_000), AutoRateFixedStep(rating = 3), NextCardStep()))
            val drag = eng.begin()
            advanceTimeBy(1_001)
            runCurrent()
            eng.setBackground(true)
            assertNull(eng.state.value.navigation)
            assertEquals(1, eng.state.value.currentCardIndex)
            eng.endNavigation(drag, CardDestination.Card("c1"))
            assertEquals(1, eng.state.value.currentCardIndex)
            eng.stop()
        }

    @Test
    fun `pause cancels a gesture without running its queued auto grade`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(this, deps, listOf(WaitStep(ms = 1_000), AutoRateFixedStep(rating = 3), NextCardStep()))
        val drag = eng.begin()
        advanceTimeBy(1_001)
        runCurrent()
        eng.pause()
        runCurrent()
        assertNull(eng.state.value.navigation)
        assertTrue(eng.isPaused.value)
        assertTrue(deps.reviews.isEmpty())
        eng.endNavigation(drag, CardDestination.Card("c2"))
        assertEquals(0, eng.state.value.currentCardIndex)
        eng.stop()
    }

    @Test
    fun `late speech recognition from a departed card cannot overwrite the new card`() =
        runTest(UnconfinedTestDispatcher()) {
            val requests = mutableListOf<CompletableDeferred<ListenResult>>()
            val deps = object : SessionDeps by FakeSessionDeps() {
                override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
                    val answer = CompletableDeferred<ListenResult>()
                    requests += answer
                    return answer.await()
                }
            }
            val eng = engine(this, deps, listOf(ShowPageStep(), ListenAndCheckStep(pageIndex = 1), ShowRatingsStep()))
            eng.endNavigation(eng.begin(), CardDestination.Card("c2"))
            assertEquals(2, requests.size)
            requests[0].complete(ListenResult("a1", ListenStatus.COMPLETED))
            runCurrent()
            assertEquals(1, eng.state.value.currentCardIndex)
            assertEquals(SessionStatus.LISTENING, eng.state.value.status)
            assertEquals(LastAnswerResult(), eng.state.value.lastAnswerResult)
            eng.stop()
        }

    @Test
    fun `resumed listening uses the speech duration of its own card`() = runTest(UnconfinedTestDispatcher()) {
        val clock = FakeClock()
        val timeouts = mutableListOf<Long>()
        val spoken = mutableListOf<String>()
        val deps = object : SessionDeps by FakeSessionDeps() {
            override suspend fun speak(text: String, lang: String, rate: Float): SpeakResult {
                spoken += text
                clock.advance(if (text == "q1") 4_000 else 10_000)
                return SpeakResult.SUCCESS
            }

            override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
                timeouts += timeoutMs
                awaitCancellation()
            }
        }
        val steps = listOf(SpeakPageStep(pageIndex = 0), ListenAndCheckStep(pageIndex = 1), ShowRatingsStep())
        val eng = engine(this, deps, steps, clock)
        eng.endNavigation(eng.begin(), CardDestination.Card("c2"))
        eng.endNavigation(eng.begin(), CardDestination.Card("c1"))
        assertEquals(listOf(22_000L, 40_000L, 22_000L), timeouts)
        assertEquals(listOf("q1", "q2"), spoken)
        eng.stop()
    }
}
