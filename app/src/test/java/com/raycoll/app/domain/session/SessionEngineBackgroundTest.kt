package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineBackgroundTest {

    @Test
    fun `background skips an unmet conditional tap gate`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(
            listOf(WaitStep(ms = 1_000), WaitForTapStep(condition = StepCondition.WRONG), NextCardStep()),
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        eng.start(listOf(card("c1")))
        eng.setBackground(true)
        advanceUntilIdle()
        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun `foreground tap interrupts a substituted background pause`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(
            listOf(WaitStep(ms = 1_000), WaitForTapStep(), NextCardStep()),
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        eng.start(listOf(card("c1")))
        eng.setBackground(true)
        advanceTimeBy(1_000)
        runCurrent()
        eng.setBackground(false)
        eng.handleCardPress()
        advanceUntilIdle()
        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    private fun card(id: String) = Flashcard(id = id, pages = listOf("front $id", "back $id"), srsState = SrsState())

    private fun group() = FlashcardGroup(
        id = "g1",
        name = "Deck",
        cards = emptyList(),
        activePageCount = 2
    )

    private fun engine(steps: List<ModeStep>, scope: TestScope, clock: Clock = FakeClock()) =
        SessionEngine(group(), steps, FakeSessionDeps(listenHangs = true), clock, 1.0f, scope)

    @Test
    fun `going to background resumes a foreground tap gate without user interaction`() =
        runTest(UnconfinedTestDispatcher()) {
            val eng = engine(
                listOf(ShowPageStep(), WaitForTapToRevealNextStep(), NextCardStep()),
                TestScope(UnconfinedTestDispatcher(testScheduler))
            )
            eng.start(listOf(card("c1"), card("c2")))
            advanceUntilIdle()
            assertEquals(InteractionGateKind.TAP_TO_REVEAL, eng.state.value.interactionGate.kind)
            eng.setBackground(true)
            advanceUntilIdle()
            assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        }

    @Test
    fun `next while paused does not restart playback until the user resumes`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(
            listOf(ShowPageStep(), WaitStep(ms = 10_000), NextCardStep()),
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        eng.start(listOf(card("c1"), card("c2")))
        eng.pause()
        assertTrue(eng.isPaused.value)
        eng.setBackground(true)
        eng.skipToNextCard()
        advanceUntilIdle()
        assertTrue(eng.isPaused.value)
        assertEquals(1, eng.state.value.currentCardIndex)
        assertFalse(eng.state.value.status == SessionStatus.FINISHED)
        eng.resume()
        advanceUntilIdle()
        assertFalse(eng.isPaused.value)
        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
    }

    @Test
    fun `background substitutes tap gate with pause and reveals next page`() = runTest(UnconfinedTestDispatcher()) {
        val eng =
            engine(
                listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), NextCardStep()),
                TestScope(UnconfinedTestDispatcher(testScheduler))
            )
        eng.setBackground(true)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()
        // po substytucji gate'a strona 1 odsłonięta i sesja poszła dalej (NextCard → karta 2)
        assertTrue(eng.state.value.currentCardIndex >= 1 || eng.state.value.status == SessionStatus.FINISHED)
    }

    @Test
    fun `foreground tap gate blocks until interaction`() = runTest(UnconfinedTestDispatcher()) {
        val eng =
            engine(
                listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), ShowRatingsStep()),
                TestScope(UnconfinedTestDispatcher(testScheduler))
            )
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        // bez tapnięcia sesja stoi na gate (karta 0, brak REVEALED)
        assertEquals(0, eng.state.value.currentCardIndex)
        assertTrue(eng.state.value.interactionGate.kind != InteractionGateKind.NONE)
    }

    @Test
    fun `goToPreviousCard within 3s goes back one card`() {
        val clock = FakeClock(1_000_000L)
        runTest(UnconfinedTestDispatcher()) {
            val eng =
                engine(
                    listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), NextCardStep()),
                    TestScope(UnconfinedTestDispatcher(testScheduler)),
                    clock
                )
            eng.start(listOf(card("c1"), card("c2"), card("c3")))
            advanceUntilIdle()
            eng.handleCardPress() // gate → karta 2
            advanceUntilIdle()
            assertEquals(1, eng.state.value.currentCardIndex)
            // w oknie 3 s → cofnięcie do karty 1
            eng.goToPreviousCard()
            advanceUntilIdle()
            assertEquals(0, eng.state.value.currentCardIndex)
        }
    }

    @Test
    fun `goToPreviousCard after 3s restarts current card`() {
        val clock = FakeClock(1_000_000L)
        runTest(UnconfinedTestDispatcher()) {
            val eng =
                engine(
                    listOf(
                        ShowPageStep(pageIndex = 0),
                        WaitForTapToRevealNextStep(),
                        ShowRatingsStep(),
                        NextCardStep()
                    ),
                    TestScope(UnconfinedTestDispatcher(testScheduler)),
                    clock
                )
            eng.start(listOf(card("c1"), card("c2"), card("c3")))
            advanceUntilIdle()
            eng.handleCardPress()
            eng.rate(3)
            advanceUntilIdle()
            assertEquals(1, eng.state.value.currentCardIndex)
            eng.handleCardPress()
            advanceUntilIdle()
            assertEquals(listOf(0, 1), eng.state.value.revealedPages.sorted())
            assertEquals(SessionStatus.REVEALED, eng.state.value.status)
            clock.current += 5_000 // poza oknem 3 s
            eng.goToPreviousCard()
            advanceUntilIdle()
            // Ten sam indeks, ale rzeczywisty restart: druga strona znów zakryta i bramka czeka.
            assertEquals(1, eng.state.value.currentCardIndex)
            assertEquals(listOf(0), eng.state.value.revealedPages.sorted())
            assertEquals(InteractionGateKind.TAP_TO_REVEAL, eng.state.value.interactionGate.kind)
        }
    }

    @Test
    fun `background does not fabricate a correct answer for skipped listening`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(
            listOf(
                ShowPageStep(pageIndex = 0),
                ListenAndCheckStep(pageIndex = 1),
                FeedbackSuccessStep(condition = StepCondition.CORRECT),
                NextCardStep(condition = StepCondition.CORRECT)
            ),
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        eng.setBackground(true)
        eng.start(listOf(card("c1"), card("c2")))
        eng.handleCardPress() // rzeczywiste przerwanie nasłuchu tapnięciem
        advanceUntilIdle()
        assertEquals(AnswerStatus.SKIPPED, eng.state.value.lastAnswerResult.status)
        assertNull(eng.state.value.lastAnswerResult.percent)
        assertEquals(0, eng.state.value.currentCardIndex)
        assertFalse(eng.state.value.cardReviewed)
        assertFalse(eng.state.value.status == SessionStatus.FINISHED)
    }

    @Test
    fun `background ratings wait for the user without recording an automatic review`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps(listenHangs = true)
            val eng = SessionEngine(
                group(),
                listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()),
                deps,
                FakeClock(),
                1.0f,
                TestScope(UnconfinedTestDispatcher(testScheduler))
            )
            eng.setBackground(true)
            eng.start(listOf(card("c1"), card("c2")))
            advanceUntilIdle()
            assertTrue(deps.reviews.isEmpty())
            assertEquals(0, eng.state.value.currentCardIndex)
            assertEquals(SessionStatus.REVEALED, eng.state.value.status)
            eng.setBackground(false)
            eng.rate(3)
            advanceUntilIdle()
            assertEquals(listOf("c1" to 3), deps.reviews.map { it.cardId to it.rating })
            assertEquals(1, eng.state.value.currentCardIndex)
        }
}
