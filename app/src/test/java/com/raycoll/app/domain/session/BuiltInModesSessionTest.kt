package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyConstants
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuiltInModesSessionTest {
    private val question = "question"
    private val answer = "answer!!"
    private val deck = FlashcardGroup("deck", "Deck", listOf(Flashcard("card", listOf(question, answer), SrsState())))

    private fun TestScope.session(mode: BuiltInMode, records: FakeSessionDeps) = SessionEngine(
        deck,
        SEED_MODES.single { it.id == mode.id }.steps,
        records,
        FakeClock(),
        1f,
        this
    )

    @Test
    fun `listen mode shows each page as it is read and waits twice its length without rating`() =
        runTest(UnconfinedTestDispatcher()) {
            val records = FakeSessionDeps()
            val engine = session(BuiltInMode.LISTEN_ONLY, records)
            val revealedWhileReading = mutableListOf<List<Int>>()
            records.onSpeak = { revealedWhileReading += engine.state.value.revealedPages }

            engine.start(deck.cards)
            advanceUntilIdle()

            assertEquals(SessionStatus.FINISHED, engine.state.value.status)
            assertEquals(listOf(question, answer), records.spoken)
            assertFalse(1 in revealedWhileReading.first())
            assertTrue(1 in revealedWhileReading.last())
            assertTrue(records.reviews.isEmpty())
            val charMs = StudyConstants.DYNAMIC_PAUSE_MS_PER_CHAR
            assertEquals(((question.length + answer.length) * charMs * 2).toLong(), currentTime)
        }

    @Test
    fun `browse mode waits for a tap before the answer and another before the next card`() =
        runTest(UnconfinedTestDispatcher()) {
            val records = FakeSessionDeps()
            val engine = session(BuiltInMode.BROWSE, records)

            engine.start(deck.cards)
            advanceUntilIdle()
            assertEquals(listOf(question), records.spoken)

            engine.handleCardPress()
            advanceUntilIdle()
            assertEquals(listOf(question, answer), records.spoken)
            assertEquals(listOf(0, 1), engine.state.value.revealedPages)

            engine.handleCardPress()
            advanceUntilIdle()
            assertEquals(SessionStatus.FINISHED, engine.state.value.status)
            assertTrue(records.reviews.isEmpty())
        }

    @Test
    fun `auto-reveal mode shows the answer on its own and then waits for a rating`() =
        runTest(UnconfinedTestDispatcher()) {
            val records = FakeSessionDeps()
            val engine = session(BuiltInMode.AUTO_REVEAL, records)

            engine.start(deck.cards)
            advanceUntilIdle()

            assertEquals(listOf(question, answer), records.spoken)
            assertEquals(SessionStatus.AWAITING_RATING, engine.state.value.status)
            assertTrue(records.reviews.isEmpty())
        }
}
