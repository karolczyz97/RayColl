package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pauza z zegarka, słuchawek albo powiadomienia: wznowienie kontynuuje kartę, nie zaczyna jej od nowa. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPauseResumeTest {
    private val card = Flashcard("c1", listOf("front", "back"), SrsState())
    private val group = FlashcardGroup("g1", "Deck", listOf(card), activePageCount = 2)

    private fun TestScope.engine(steps: List<ModeStep>, deps: FakeSessionDeps, clock: FakeClock = FakeClock()) =
        SessionEngine(group, steps, deps, clock, 1f, TestScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `resume continues from the step that was interrupted instead of the start of the card`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val eng = engine(
                listOf(
                    SpeakPageStep(pageIndex = 0),
                    WaitStep(ms = 10_000),
                    SpeakPageStep(pageIndex = 1),
                    NextCardStep()
                ),
                deps
            )
            eng.start(listOf(card))
            runCurrent()
            assertEquals(listOf("front"), deps.spoken)

            eng.pause()
            eng.resume()
            advanceUntilIdle()

            assertEquals(listOf("front", "back"), deps.spoken)
            assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        }

    @Test
    fun `a wait interrupted by pause resumes with the time that was left`() = runTest(UnconfinedTestDispatcher()) {
        val clock = FakeClock()
        val eng = engine(listOf(WaitStep(ms = 10_000), NextCardStep()), FakeSessionDeps(), clock)
        eng.start(listOf(card))
        advanceTimeBy(4_000)
        clock.advance(4_000)

        eng.pause()
        eng.resume()
        val resumedAt = currentTime
        advanceTimeBy(5_999)
        assertTrue(eng.state.value.status != SessionStatus.FINISHED)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        assertEquals(resumedAt + 6_000, currentTime)
    }

    @Test
    fun `resuming after the rating does not rate the card a second time`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(
            listOf(ShowPageStep(), AutoRateFixedStep(rating = 3), WaitStep(ms = 5_000), NextCardStep()),
            deps
        )
        eng.start(listOf(card))
        runCurrent()
        assertEquals(1, deps.reviews.size)

        eng.pause()
        eng.resume()
        advanceUntilIdle()

        assertEquals(1, deps.reviews.size)
        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
    }

    @Test
    fun `leaving the card and coming back starts its wait from scratch`() = runTest(UnconfinedTestDispatcher()) {
        val clock = FakeClock()
        val eng = engine(listOf(WaitStep(ms = 10_000), NextCardStep()), FakeSessionDeps(), clock)
        eng.start(listOf(card))
        advanceTimeBy(4_000)
        clock.advance(4_000)

        eng.restartCurrentCard()
        val restartedAt = currentTime
        advanceTimeBy(9_999)
        assertTrue(eng.state.value.status != SessionStatus.FINISHED)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        assertEquals(restartedAt + 10_000, currentTime)
    }
}
