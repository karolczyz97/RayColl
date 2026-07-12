package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock(var now: Long = 0L) : Clock {
    override fun now(): Long = now
}

private class FakeDeps : SessionDeps {
    val reviewed = mutableListOf<Pair<String, Int>>()
    override suspend fun speak(text: String, lang: String, rate: Float): SpeakResult = SpeakResult.SUCCESS
    override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult =
        ListenResult("", ListenStatus.SKIPPED)
    override fun feedbackSuccess() {}
    override fun feedbackError() {}
    override fun haptic(rating: Int) {}
    override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {
        reviewed.add(cardId to rating)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineBackgroundTest {

    private fun card(id: String) = Flashcard(id = id, pages = listOf("front $id", "back $id"), srsState = SrsState())

    private fun group() = FlashcardGroup(
        id = "g1", name = "Deck", cards = emptyList(), activePageCount = 2,
    )

    private fun engine(steps: List<ModeStep>, scope: TestScope, clock: Clock = FakeClock()) =
        SessionEngine(group(), steps, FakeDeps(), clock, 1.0f, scope)

    @Test
    fun `background substitutes tap gate with pause and reveals next page`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), NextCardStep()), TestScope(UnconfinedTestDispatcher(testScheduler)))
        eng.setBackground(true)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()
        // po substytucji gate'a strona 1 odsłonięta i sesja poszła dalej (NextCard → karta 2)
        assertTrue(eng.state.value.currentCardIndex >= 1 || eng.state.value.status == SessionStatus.FINISHED)
    }

    @Test
    fun `foreground tap gate blocks until interaction`() = runTest(UnconfinedTestDispatcher()) {
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), ShowRatingsStep()), TestScope(UnconfinedTestDispatcher(testScheduler)))
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
            val eng = engine(listOf(ShowPageStep(pageIndex = 0), NextCardStep()), TestScope(UnconfinedTestDispatcher(testScheduler)), clock)
            eng.start(listOf(card("c1"), card("c2"), card("c3")))
            advanceUntilIdle()
            // sesja przeleciała wszystkie karty (NextCard bez gate) — użyjemy wariantu z gatem
        }
        val clock2 = FakeClock(1_000_000L)
        runTest(UnconfinedTestDispatcher()) {
            val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), NextCardStep()), TestScope(UnconfinedTestDispatcher(testScheduler)), clock2)
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
            val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealNextStep(), NextCardStep()), TestScope(UnconfinedTestDispatcher(testScheduler)), clock)
            eng.start(listOf(card("c1"), card("c2"), card("c3")))
            advanceUntilIdle()
            eng.handleCardPress()
            advanceUntilIdle()
            assertEquals(1, eng.state.value.currentCardIndex)
            clock.now += 5_000 // poza oknem 3 s
            eng.goToPreviousCard()
            advanceUntilIdle()
            // restart bieżącej karty od kroku 0 — indeks bez zmian
            assertEquals(1, eng.state.value.currentCardIndex)
        }
    }
}
