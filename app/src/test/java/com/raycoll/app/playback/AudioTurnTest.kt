package com.raycoll.app.playback

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.session.SpeakResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mowa i nasłuch nigdy naraz — także przy szybkim przełączaniu kart i przerwaniu sesji. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioTurnTest {

    /** Atrapa głośnika i mikrofonu: pilnuje, że nic nie gra jednocześnie, i zapisuje kolejność zdarzeń. */
    private class AudioDeps(private val scope: TestScope) : SessionDeps {
        private val turn = AudioTurn()
        val events = mutableListOf<String>()
        var active: String? = null
        var overlaps = 0

        private suspend fun <T> use(name: String, durationMs: Long, result: T): T = turn.withTurn {
            if (active != null) overlaps++
            active = name
            events += "start $name"
            try {
                delay(durationMs)
                result
            } catch (e: CancellationException) {
                // zwolnienie zasobu trwa chwilę (zatrzymanie syntezy / rozpoznawania)
                withContext(NonCancellable) { delay(20) }
                throw e
            } finally {
                events += "end $name"
                active = null
            }
        }

        override suspend fun speak(text: String, lang: String, rate: Float) =
            use("speak:$text", 500, SpeakResult.SUCCESS)

        override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit) =
            use("listen", 800, ListenResult("", ListenStatus.COMPLETED))

        override fun feedbackSuccess() {}
        override fun feedbackError() {}
        override fun haptic(rating: Int) {}
        override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {}
    }

    private val clock = object : Clock {
        override fun now(): Long = 0L
    }

    private fun card(id: String) = Flashcard(id = id, pages = listOf("q$id", "a$id"), srsState = SrsState())

    @Test
    fun `next turn starts only after the interrupted one released the resource`() = runTest {
        val turn = AudioTurn()
        val events = mutableListOf<String>()
        val speaking = launch {
            turn.withTurn {
                events += "speak start"
                try {
                    delay(1_000)
                } finally {
                    withContext(NonCancellable) {
                        delay(50)
                        events += "speak released"
                    }
                }
            }
        }
        runCurrent()
        val listening = launch { turn.withTurn { events += "listen start" } }
        runCurrent()
        speaking.cancel()
        advanceUntilIdle()
        listening.join()
        assertEquals(listOf("speak start", "speak released", "listen start"), events)
    }

    @Test
    fun `rapid next and previous never run the microphone while speaking`() = runTest(StandardTestDispatcher()) {
        val deps = AudioDeps(this)
        val engine = SessionEngine(
            FlashcardGroup(id = "g", name = "Deck", cards = emptyList(), activePageCount = 2),
            listOf(
                ShowPageStep(pageIndex = 0),
                SpeakPageStep(pageIndex = 0),
                ListenAndCheckStep(pageIndex = 1),
                NextCardStep()
            ),
            deps,
            clock,
            1f,
            backgroundScope
        )
        engine.start((1..6).map { card("$it") })
        repeat(12) { i ->
            advanceTimeBy(130L + i * 37 % 300)
            if (i % 3 == 2) engine.goToPreviousCard() else engine.skipToNextCard()
        }
        advanceTimeBy(5_000)
        assertEquals("mowa i nasłuch nachodziły na siebie: ${deps.events}", 0, deps.overlaps)

        engine.stop()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(SessionStatus.FINISHED, engine.state.value.status)
        assertEquals("po zakończeniu sesji nic nie gra ani nie słucha", null, deps.active)
        assertTrue(deps.events.isNotEmpty())
        assertFalse(deps.events.last().startsWith("start"))
    }
}
