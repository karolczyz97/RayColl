package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MutableClock(var now: Long = 1_000_000L) : Clock {
    override fun now(): Long = now
}

private data class ReviewRecord(val cardId: String, val rating: Int, val replaceFromBase: SrsState?)

private class RecordingDeps(
    var speakResult: SpeakResult = SpeakResult.SUCCESS,
    var listenHangs: Boolean = false,
) : SessionDeps {
    val reviews = mutableListOf<ReviewRecord>()
    override suspend fun speak(text: String, lang: String, rate: Float): SpeakResult = speakResult
    override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
        if (listenHangs) awaitCancellation()
        return ListenResult("", ListenStatus.COMPLETED)
    }
    override fun feedbackSuccess() {}
    override fun feedbackError() {}
    override fun haptic(rating: Int) {}
    override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {
        reviews.add(ReviewRecord(cardId, rating, replaceFromBase))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineSemanticsTest {

    private fun card(id: String, reps: Int = 0) = Flashcard(
        id = id,
        pages = listOf("front $id", "back $id"),
        srsState = SrsState(repetitions = reps),
    )

    private fun group() = FlashcardGroup(
        id = "g1", name = "Deck", cards = emptyList(), activePageCount = 2,
        pageLanguages = listOf("en-US", "pl-PL"),
    )

    private fun TestScope.engine(steps: List<ModeStep>, deps: RecordingDeps, clock: Clock = MutableClock()) =
        SessionEngine(group(), steps, deps, clock, 1.0f, TestScope(UnconfinedTestDispatcher(testScheduler)))

    // --- zapis ocen ---

    @Test
    fun `rate persists review and rating 1 adds card to failed`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)

        eng.rate(1)
        advanceUntilIdle()
        assertEquals(listOf(ReviewRecord("c1", 1, null)), deps.reviews)
        assertTrue(eng.state.value.failedCardIds.contains("c1"))
        assertEquals(1, eng.state.value.currentCardIndex)
    }

    @Test
    fun `re-rate after goToPreviousCard overwrites review from base srs and resets failed`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val clock = MutableClock()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()), deps, clock)
        val baseSrs = SrsState(repetitions = 2)
        eng.start(listOf(card("c1").copy(srsState = baseSrs), card("c2")))
        advanceUntilIdle()

        eng.rate(1) // pierwsza ocena: bez bazy, c1 na failed
        advanceUntilIdle()
        eng.goToPreviousCard() // w oknie 3 s → powrót do c1
        advanceUntilIdle()
        assertEquals(0, eng.state.value.currentCardIndex)

        eng.rate(4) // ponowna ocena NADPISUJE: SRS od bazy, failed od nowa
        advanceUntilIdle()
        assertEquals(2, deps.reviews.size)
        assertNull(deps.reviews[0].replaceFromBase)
        assertEquals(baseSrs, deps.reviews[1].replaceFromBase)
        assertFalse(eng.state.value.failedCardIds.contains("c1"))
    }

    @Test
    fun `restartFailed starts new attempt with failed cards only`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        val cards = listOf(card("c1"), card("c2"))
        eng.start(cards)
        advanceUntilIdle()
        eng.rate(1) // c1 failed
        advanceUntilIdle()
        eng.rate(3) // c2 ok → FINISHED
        advanceUntilIdle()
        assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        val failedIds = eng.state.value.failedCardIds
        assertEquals(setOf("c1"), failedIds)

        eng.restartFailed(cards.filter { it.id in failedIds })
        advanceUntilIdle()
        assertEquals(1, eng.state.value.cards.size)
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)

        // nowa próba: ocena c1 znowu bez replaceFromBase
        eng.rate(3)
        advanceUntilIdle()
        assertNull(deps.reviews.last().replaceFromBase)
        assertEquals("c1", deps.reviews.last().cardId)
    }

    // --- tap = skip kroku, nie karty ---

    @Test
    fun `tap during wait skips the step but stays on the same card`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        advanceTimeBy(100)
        assertEquals(0, eng.state.value.currentCardIndex)
        assertFalse(eng.state.value.status == SessionStatus.REVEALED)

        eng.handleCardPress() // skip czekania
        advanceUntilIdle()
        assertEquals(0, eng.state.value.currentCardIndex) // wciąż ta sama karta
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)
    }

    @Test
    fun `tap during listening stops stt with skipped result without rating`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps(listenHangs = true)
        val eng = engine(
            listOf(ShowPageStep(pageIndex = 0), ListenAndCheckStep(pageIndex = 1, successThreshold = 70), ShowRatingsStep()),
            deps,
        )
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertEquals(SessionStatus.LISTENING, eng.state.value.status)

        eng.handleCardPress() // stop nasłuchu → 'skipped'
        advanceUntilIdle()
        assertEquals(AnswerStatus.SKIPPED, eng.state.value.lastAnswerResult.status)
        assertTrue(deps.reviews.isEmpty()) // bez auto-oceny
        assertEquals(SessionStatus.REVEALED, eng.state.value.status) // manual fallback
    }

    // --- peek (hold) ---

    @Test
    fun `press held past threshold peeks next hidden page and release hides it`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val clock = MutableClock()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps, clock)
        eng.start(listOf(card("c1")))
        advanceTimeBy(100)

        eng.pressDown()
        advanceTimeBy(600) // > PEEK_HOLD_THRESHOLD_MS
        assertEquals(1, eng.state.value.peekedPageIndex)

        clock.now += 600
        eng.pressUp() // trzymane >=500 ms → strona chowa się z powrotem
        advanceUntilIdle()
        assertNull(eng.state.value.peekedPageIndex)
        assertFalse(eng.state.value.revealedPages.contains(1))
    }

    @Test
    fun `short press acts as tap`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val clock = MutableClock()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps, clock)
        eng.start(listOf(card("c1")))
        advanceTimeBy(100)

        eng.pressDown()
        clock.now += 100 // < 500 ms
        eng.pressUp() // = tap → skip czekania
        advanceUntilIdle()
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)
    }

    // --- błędy TTS ---

    @Test
    fun `tts error in foreground sets error message and session continues`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps(speakResult = SpeakResult.ERROR)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertEquals(SessionEngine.ERROR_TTS, eng.state.value.errorMsg)
        // sesja nie crashuje — dochodzi do ocen
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)
    }

    @Test
    fun `missing tts voice reports dedicated error key`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps(speakResult = SpeakResult.MISSING_VOICE)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertEquals(SessionEngine.ERROR_TTS_MISSING_VOICE, eng.state.value.errorMsg)
    }

    @Test
    fun `tts error in background auto-pauses instead of error status`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps(speakResult = SpeakResult.ERROR)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), NextCardStep()), deps)
        var autoPaused = false
        eng.onBackgroundAutoPause = { autoPaused = true }
        eng.setBackground(true)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()
        assertTrue(autoPaused)
        assertNull(eng.state.value.errorMsg)
        assertEquals(0, eng.state.value.currentCardIndex) // łańcuch zatrzymany
    }

    // --- status nie wycieka między kartami (tap-gate pozostaje klikalny) ---

    @Test
    fun `advancing to next card resets status so a silent reveal gate stays tappable`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        // Tryb bez speak/listen: sam show_page + bramka + oceny. Poprzednia karta kończy
        // się na REVEALED; po przejściu status MUSI wrócić do IDLE, inaczej tap w bramkę
        // drugiej karty byłby połykany (REVEALED → NIC) i użytkownik utknąłby.
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealStep(), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()

        eng.handleCardPress() // c1: domknij bramkę → oceny
        advanceUntilIdle()
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)
        eng.rate(3) // → c2
        advanceUntilIdle()

        assertEquals(1, eng.state.value.currentCardIndex)
        assertEquals(InteractionGateKind.TAP_TO_REVEAL, eng.state.value.interactionGate.kind)
        assertFalse(eng.state.value.status == SessionStatus.REVEALED) // status nie wyciekł
        eng.handleCardPress() // tap w bramkę drugiej karty MUSI zadziałać
        advanceUntilIdle()
        assertEquals(SessionStatus.REVEALED, eng.state.value.status)
    }

    // --- okno "poprzednia" vs restart nie resetuje się po tap-gate ---

    @Test
    fun `tap-gate resume does not reset the previous-card window`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps()
        val clock = MutableClock(1_000L)
        // Tryb z bramką tap-to-reveal (strona 1 ukryta na starcie karty).
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealStep(), ShowRatingsStep()), deps, clock)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()

        // c1: domknij bramkę i oceń → przejście do c2 (start c2 przy zegarze 1000).
        eng.handleCardPress()
        advanceUntilIdle()
        eng.rate(3)
        advanceUntilIdle()
        assertEquals(1, eng.state.value.currentCardIndex)

        // c2 stoi na bramce; domykamy ją DUŻO później (zegar 5000).
        clock.now = 5_000L
        eng.handleCardPress()
        advanceUntilIdle()

        // "Poprzednia" po kolejnych 500 ms: od PRAWDZIWEGO startu c2 (1000) minęło 4500 ms
        // (> 3000) → RESTART bieżącej karty, NIE powrót do c1. Bug resetował znacznik na
        // tapnięciu w bramkę (5000) i błędnie cofał do c1.
        clock.now = 5_500L
        eng.goToPreviousCard()
        advanceUntilIdle()
        assertEquals(1, eng.state.value.currentCardIndex)
    }

    @Test
    fun `clearError clears message only`() = runTest(UnconfinedTestDispatcher()) {
        val deps = RecordingDeps(speakResult = SpeakResult.ERROR)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertNotNull(eng.state.value.errorMsg)
        eng.clearError()
        assertNull(eng.state.value.errorMsg)
    }
}
