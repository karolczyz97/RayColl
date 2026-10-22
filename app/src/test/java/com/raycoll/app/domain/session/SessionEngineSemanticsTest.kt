package com.raycoll.app.domain.session

import com.raycoll.app.core.Clock
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import com.raycoll.app.testing.ReviewRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineSemanticsTest {

    @Test
    fun `swiping back after the restart window returns to the previous card without a review`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val clock = FakeClock(1_000L)
            val eng = engine(listOf(ShowPageStep(), WaitForTapToRevealStep(), ShowRatingsStep()), deps, clock)
            eng.start(listOf(card("c1"), card("c2")))
            eng.skipToNextCard()
            advanceUntilIdle()
            clock.advance(10_000)
            val current = eng.state.value
            val navigation = requireNotNull(eng.beginNavigation(current.sessionId, "c2"))
            eng.endNavigation(navigation, CardDestination.Card("c1"))
            advanceUntilIdle()
            assertEquals(0, eng.state.value.currentCardIndex)
            assertEquals(listOf(0), eng.state.value.revealedPages)
            assertTrue(deps.reviews.isEmpty())
            eng.stop()
        }

    @Test
    fun `touching a revealed card does not block rating or advancement`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(listOf(ShowPageStep(), WaitForTapToRevealStep(), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        eng.handleCardPress()
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
        eng.pressDown()
        eng.rate(4)
        advanceUntilIdle()
        assertEquals(1, eng.state.value.currentCardIndex)
        assertEquals(listOf(ReviewRecord("c1", 4, null)), deps.reviews)
    }

    @Test
    fun `early and double ratings cannot review an unrevealed card`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(listOf(WaitStep(ms = 1_000), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        eng.rate(3)
        assertTrue(deps.reviews.isEmpty())
        advanceTimeBy(1_000)
        runCurrent()
        eng.rate(3)
        eng.rate(4)
        assertEquals(1, deps.reviews.size)
        assertEquals("c1", deps.reviews.single().cardId)
        assertEquals(1, eng.state.value.currentCardIndex)
        eng.stop()
    }

    @Test
    fun `reveal styles show all four pages before ratings`() = runTest(UnconfinedTestDispatcher()) {
        for (style in RevealStyle.entries) {
            val eng = SessionEngine(
                group().copy(activePageCount = 4),
                listOf(CompoundStep(params = CompoundParams.FlipReveal(style))),
                FakeSessionDeps(),
                FakeClock(),
                1f,
                TestScope(UnconfinedTestDispatcher(testScheduler))
            )
            eng.start(listOf(card("c1").copy(pages = listOf("q", "a", "example", "note"))))
            eng.handleCardPress()
            if (style == RevealStyle.NEXT) {
                assertEquals(listOf(0, 1), eng.state.value.revealedPages)
                assertNotEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
                eng.handleCardPress()
                assertEquals(listOf(0, 1, 2), eng.state.value.revealedPages)
                eng.handleCardPress()
            }
            assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
            assertEquals(listOf(0, 1, 2, 3), eng.state.value.revealedPages)
            eng.stop()
        }
    }

    @Test
    fun `blank expected answer skips STT and auto rating then allows manual rating`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps(listenHangs = true)
            val eng =
                engine(listOf(ListenAndCheckStep(pageIndex = 1), AutoRateFromAnswerStep(), ShowRatingsStep()), deps)
            eng.start(listOf(card("c1").copy(pages = listOf("q", "  "))))
            advanceUntilIdle()
            assertEquals(AnswerStatus.SKIPPED, eng.state.value.lastAnswerResult.status)
            assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
            assertTrue(deps.reviews.isEmpty())
            eng.rate(3)
            assertEquals(3, deps.reviews.single().rating)
        }

    private fun card(id: String, reps: Int = 0) = Flashcard(
        id = id,
        pages = listOf("front $id", "back $id"),
        srsState = SrsState(repetitions = reps)
    )

    private fun group() = FlashcardGroup(
        id = "g1",
        name = "Deck",
        cards = emptyList(),
        activePageCount = 2,
        pageLanguages = listOf("en-US", "pl-PL")
    )

    private fun TestScope.engine(steps: List<ModeStep>, deps: FakeSessionDeps, clock: Clock = FakeClock()) =
        SessionEngine(group(), steps, deps, clock, 1.0f, TestScope(UnconfinedTestDispatcher(testScheduler)))

    // --- warunki kroków ---

    @Test
    fun `pause clears listening status and a paused tap does not consume the gate`() =
        runTest(UnconfinedTestDispatcher()) {
            val eng = engine(listOf(ListenAndCheckStep(pageIndex = 1)), FakeSessionDeps(listenHangs = true))
            eng.start(listOf(card("c1")))
            assertEquals(SessionStatus.LISTENING, eng.state.value.status)
            eng.pause()
            assertEquals(SessionStatus.IDLE, eng.state.value.status)
            assertNull(eng.state.value.audioPageIndex)
            eng.stop()

            val gated = engine(listOf(WaitForTapToRevealStep(), ShowRatingsStep()), FakeSessionDeps())
            gated.start(listOf(card("c1")))
            gated.pause()
            val before = gated.state.value
            gated.handleCardPress()
            assertEquals(before, gated.state.value)
            gated.stop()
        }

    @Test
    fun `wrong answer skips the correct-only next card and runs the wrong branch`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps() // pusta transkrypcja → 0% → INCORRECT
            val eng = engine(
                listOf(
                    ListenAndCheckStep(pageIndex = 1, successThreshold = 70),
                    NextCardStep(condition = StepCondition.CORRECT),
                    AutoRateFixedStep(rating = 1, condition = StepCondition.WRONG),
                    NextCardStep(condition = StepCondition.WRONG)
                ),
                deps
            )
            eng.start(listOf(card("c1"), card("c2")))
            advanceUntilIdle()

            // każda karta przeszła gałąź "wrong" (ocena 1), żadna nie została porzucona bez oceny
            assertEquals(listOf(ReviewRecord("c1", 1, null), ReviewRecord("c2", 1, null)), deps.reviews)
            assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        }

    @Test
    fun `correct answer of the previous card does not trigger a correct-only step of the next card`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps(transcript = "answer")
            val eng = engine(
                listOf(
                    // przed pierwszym nasłuchem karty: wynik jeszcze nie istnieje
                    AutoRateFixedStep(rating = 4, condition = StepCondition.CORRECT),
                    ListenAndCheckStep(pageIndex = 1, successThreshold = 70),
                    NextCardStep()
                ),
                deps
            )
            val pages = listOf("question", "answer")
            eng.start(listOf(card("c1").copy(pages = pages), card("c2").copy(pages = pages)))
            advanceUntilIdle()

            // c1 odpowiedziała poprawnie, ale c2 nie dostała oceny z cudzego wyniku
            assertEquals(emptyList<ReviewRecord>(), deps.reviews)
            assertEquals(SessionStatus.FINISHED, eng.state.value.status)
        }

    @Test
    fun `blank page is shown but not sent to text-to-speech`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(
            listOf(
                ShowPageStep(pageIndex = 0),
                SpeakPageStep(pageIndex = 0),
                ShowPageStep(pageIndex = 1),
                SpeakPageStep(pageIndex = 1),
                ShowRatingsStep()
            ),
            deps
        )
        eng.start(listOf(card("c1").copy(pages = listOf("question", "  "))))
        advanceUntilIdle()

        assertEquals(listOf("question"), deps.spoken)
        // pusta strona nadal jest odsłonięta, a sesja doszła do ocen bez czekania na mowę
        assertEquals(listOf(0, 1), eng.state.value.revealedPages)
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
        assertNull(eng.state.value.error)
    }

    // --- zapis ocen ---

    @Test
    fun `rate persists review and rating 1 adds card to failed`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)

        eng.rate(1)
        advanceUntilIdle()
        assertEquals(listOf(ReviewRecord("c1", 1, null)), deps.reviews)
        assertTrue(eng.state.value.failedCardIds.contains("c1"))
        assertEquals(1, eng.state.value.currentCardIndex)
    }

    @Test
    fun `re-rate after goToPreviousCard overwrites review from base srs and resets failed`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val clock = FakeClock()
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
        val deps = FakeSessionDeps()
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

        eng.start(cards.filter { it.id in failedIds })
        advanceUntilIdle()
        assertEquals(1, eng.state.value.cards.size)
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)

        // nowa próba: ocena c1 znowu bez replaceFromBase
        eng.rate(3)
        advanceUntilIdle()
        assertNull(deps.reviews.last().replaceFromBase)
        assertEquals("c1", deps.reviews.last().cardId)
    }

    // --- tap = skip kroku, nie karty ---

    @Test
    fun `tap during wait skips the step but stays on the same card`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        advanceTimeBy(100)
        assertEquals(0, eng.state.value.currentCardIndex)
        assertFalse(eng.state.value.status == SessionStatus.AWAITING_RATING)

        eng.handleCardPress() // skip czekania
        advanceUntilIdle()
        assertEquals(0, eng.state.value.currentCardIndex) // wciąż ta sama karta
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
    }

    @Test
    fun `tap during listening stops stt with skipped result without rating`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps(listenHangs = true)
        val eng = engine(
            listOf(
                ShowPageStep(pageIndex = 0),
                ListenAndCheckStep(pageIndex = 1, successThreshold = 70),
                ShowRatingsStep()
            ),
            deps
        )
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertEquals(SessionStatus.LISTENING, eng.state.value.status)

        eng.handleCardPress() // stop nasłuchu → 'skipped'
        advanceUntilIdle()
        assertEquals(AnswerStatus.SKIPPED, eng.state.value.lastAnswerResult.status)
        assertTrue(deps.reviews.isEmpty()) // bez auto-oceny
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status) // manual fallback
    }

    // --- peek (hold) ---

    @Test
    fun `press held past threshold peeks next hidden page and release hides it`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps()
            val clock = FakeClock()
            val eng =
                engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps, clock)
            eng.start(listOf(card("c1")))
            advanceTimeBy(100)

            eng.pressDown()
            advanceTimeBy(600) // > PEEK_HOLD_THRESHOLD_MS
            assertEquals(1, eng.state.value.peekedPageIndex)

            clock.current += 600
            eng.pressUp() // trzymane >=500 ms → strona chowa się z powrotem
            advanceUntilIdle()
            assertNull(eng.state.value.peekedPageIndex)
            assertFalse(eng.state.value.revealedPages.contains(1))
        }

    @Test
    fun `short press acts as tap`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val clock = FakeClock()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps, clock)
        eng.start(listOf(card("c1")))
        advanceTimeBy(100)

        eng.pressDown()
        clock.current += 100 // < 500 ms
        eng.pressUp() // = tap → skip czekania
        advanceUntilIdle()
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
    }

    @Test
    fun `cancelPress clears peek and does not trigger tap action`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val clock = FakeClock()
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), deps, clock)
        eng.start(listOf(card("c1")))
        advanceTimeBy(100)

        eng.pressDown()
        advanceTimeBy(600)
        assertEquals(1, eng.state.value.peekedPageIndex)

        eng.cancelPress()
        advanceTimeBy(100)
        assertNull(eng.state.value.peekedPageIndex)
        assertFalse(eng.state.value.revealedPages.contains(1))
        assertNotEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
    }

    // --- błędy TTS ---

    @Test
    fun `tts error in foreground sets error message and session continues`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps(speakResult = SpeakResult.ERROR)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertEquals(SessionError.TTS, eng.state.value.error)
        // sesja nie crashuje — dochodzi do ocen
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
    }

    @Test
    fun `missing tts voice reports dedicated error key`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps(speakResult = SpeakResult.MISSING_VOICE)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertEquals(SessionError.TTS_MISSING_VOICE, eng.state.value.error)
    }

    @Test
    fun `tts error in background auto-pauses instead of error status`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps(speakResult = SpeakResult.ERROR)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), NextCardStep()), deps)
        eng.setBackground(true)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()
        assertTrue(eng.isPaused.value)
        assertNull(eng.state.value.error)
        assertEquals(0, eng.state.value.currentCardIndex) // łańcuch zatrzymany
    }

    // --- status nie wycieka między kartami (tap-gate pozostaje klikalny) ---

    @Test
    fun `next card resets status and keeps the reveal gate tappable`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        // Tryb bez speak/listen: sam show_page + bramka + oceny. Poprzednia karta kończy
        // się na AWAITING_RATING; po przejściu status MUSI wrócić do IDLE, inaczej tap w bramkę
        // drugiej karty byłby połykany (AWAITING_RATING → NIC) i użytkownik utknąłby.
        val eng = engine(listOf(ShowPageStep(pageIndex = 0), WaitForTapToRevealStep(), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1"), card("c2")))
        advanceUntilIdle()

        eng.handleCardPress() // c1: domknij bramkę → oceny
        advanceUntilIdle()
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
        eng.rate(3) // → c2
        advanceUntilIdle()

        assertEquals(1, eng.state.value.currentCardIndex)
        assertEquals(InteractionGateKind.TAP_TO_REVEAL, eng.state.value.interactionGate.kind)
        assertFalse(eng.state.value.status == SessionStatus.AWAITING_RATING) // status nie wyciekł
        eng.handleCardPress() // tap w bramkę drugiej karty MUSI zadziałać
        advanceUntilIdle()
        assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
    }

    // --- okno "poprzednia" vs restart nie resetuje się po tap-gate ---

    @Test
    fun `tap-gate resume preserves the previous-card window`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val clock = FakeClock(1_000L)
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
        clock.current = 5_000L
        eng.handleCardPress()
        advanceUntilIdle()

        // "Poprzednia" po kolejnych 500 ms: od PRAWDZIWEGO startu c2 (1000) minęło 4500 ms
        // (> 3000) → RESTART bieżącej karty, NIE powrót do c1. Bug resetował znacznik na
        // tapnięciu w bramkę (5000) i błędnie cofał do c1.
        clock.current = 5_500L
        eng.goToPreviousCard()
        advanceUntilIdle()
        assertEquals(1, eng.state.value.currentCardIndex)
    }

    @Test
    fun `clearError clears message only`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps(speakResult = SpeakResult.ERROR)
        val eng = engine(listOf(SpeakPageStep(pageIndex = 0), ShowRatingsStep()), deps)
        eng.start(listOf(card("c1")))
        advanceUntilIdle()
        assertNotNull(eng.state.value.error)
        eng.clearError()
        assertNull(eng.state.value.error)
    }

    @Test
    fun `listen that throws becomes an STT error and the session goes on to manual rating`() =
        runTest(UnconfinedTestDispatcher()) {
            val deps = FakeSessionDeps(listenThrows = true)
            val eng = engine(listOf(ListenAndCheckStep(pageIndex = 1), ShowRatingsStep()), deps)
            eng.start(listOf(card("c1")))
            advanceUntilIdle()
            assertEquals(AnswerStatus.ERROR, eng.state.value.lastAnswerResult.status)
            assertEquals(SessionError.STT, eng.state.value.error)
            assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
        }

    @Test
    fun `peek targets the page hidden when the hold threshold passes`() = runTest(UnconfinedTestDispatcher()) {
        val clock = FakeClock()
        val eng = SessionEngine(
            group().copy(activePageCount = 3),
            listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()),
            FakeSessionDeps(),
            clock,
            1f,
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        eng.start(listOf(card("c1").copy(pages = listOf("q", "a", "extra"))))
        advanceTimeBy(100)

        eng.pressDown() // przy naciśnięciu pierwsza ukryta to strona 1
        advanceTimeBy(200)
        eng.dispatch(SessionAction.RevealPages(listOf(1))) // odsłonięta, zanim minie próg
        advanceTimeBy(400)
        assertEquals(2, eng.state.value.peekedPageIndex)
        eng.stop()
    }

    @Test
    fun `peek does nothing when every page got revealed during the hold`() = runTest(UnconfinedTestDispatcher()) {
        val eng =
            engine(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000L), ShowRatingsStep()), FakeSessionDeps())
        eng.start(listOf(card("c1")))
        advanceTimeBy(100)
        eng.pressDown()
        eng.dispatch(SessionAction.RevealPages(listOf(1)))
        advanceTimeBy(600)
        assertNull(eng.state.value.peekedPageIndex)
        eng.stop()
    }

    // --- kroki wykonywane przez StepExecutor ---

    @Test
    fun `dynamic pause lasts 60 ms per character of the next page times the multiplier`() =
        runTest(UnconfinedTestDispatcher()) {
            val eng = engine(
                listOf(
                    DynamicPauseStep(nextPageIndex = 1, pauseMultiplier = 1.5),
                    ShowPageStep(pageIndex = 1),
                    ShowRatingsStep()
                ),
                FakeSessionDeps()
            )
            // "back c1" = 7 znaków → 7 * 60 ms * 1.5 = 630 ms
            eng.start(listOf(card("c1")))
            advanceTimeBy(629)
            runCurrent()
            assertFalse(1 in eng.state.value.revealedPages)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(1 in eng.state.value.revealedPages)
            assertEquals(SessionStatus.AWAITING_RATING, eng.state.value.status)
            eng.stop()
        }

    @Test
    fun `speak all pages reads every active page in order`() = runTest(UnconfinedTestDispatcher()) {
        val deps = FakeSessionDeps()
        val eng = SessionEngine(
            group().copy(activePageCount = 3),
            listOf(SpeakAllPagesStep(), ShowRatingsStep()),
            deps,
            FakeClock(),
            1f,
            TestScope(UnconfinedTestDispatcher(testScheduler))
        )
        eng.start(listOf(card("c1").copy(pages = listOf("one", "two", "three", "four"))))
        advanceUntilIdle()
        assertEquals(listOf("one", "two", "three"), deps.spoken)
        eng.stop()
    }

    @Test
    fun `listening time is 5 s or three times the last speech plus 10 s`() = runTest(UnconfinedTestDispatcher()) {
        val steps = listOf(SpeakPageStep(pageIndex = 0), ListenAndCheckStep(pageIndex = 1), ShowRatingsStep())
        fun timeoutAfterSpeechOf(speechMs: Long?): Long {
            val clock = FakeClock()
            val deps = FakeSessionDeps().apply { onSpeak = { clock.advance(speechMs ?: 0) } }
            val eng = engine(if (speechMs == null) steps.drop(1) else steps, deps, clock)
            eng.start(listOf(card("c1")))
            advanceUntilIdle()
            eng.stop()
            return deps.listenTimeouts.single()
        }
        // bez wypowiedzi i po krótkiej (3 × 1 s < 5 s): minimum 5 s + 10 s zapasu
        assertEquals(15_000L, timeoutAfterSpeechOf(null))
        assertEquals(15_000L, timeoutAfterSpeechOf(1_000))
        // po długiej: 3 × 4 s + 10 s
        assertEquals(22_000L, timeoutAfterSpeechOf(4_000))
    }
}
