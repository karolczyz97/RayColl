package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRetryRatingTest {
    @Test fun `second correct answer saves HARD and shows the same grade instead of the raw speech match`() =
        runTest(UnconfinedTestDispatcher()) {
            val calls = listOf("wrong", "answer").iterator()
            val records = FakeSessionDeps()
            lateinit var engine: SessionEngine
            var shownAtSave: Int? = null
            val deps = object : SessionDeps by records {
                override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit) =
                    ListenResult(calls.next(), ListenStatus.COMPLETED)

                override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {
                    shownAtSave = engine.state.value.lastAnswerResult.suggestedRating
                    records.onCardReviewed(deckId, cardId, rating, replaceFromBase)
                }
            }
            val card = Flashcard("card", listOf("question", "answer"), SrsState())
            val deck = FlashcardGroup("deck", "Deck", listOf(card))
            engine = SessionEngine(
                deck,
                SEED_MODES.single { it.id == BuiltInMode.LISTEN_SPEAK.id }.steps,
                deps,
                FakeClock(),
                1f,
                this
            )
            engine.start(deck.cards)
            advanceUntilIdle()
            assertEquals(SessionStatus.FINISHED, engine.state.value.status)
            assertEquals(Ratings.HARD, records.reviews.single().rating)
            assertEquals(Ratings.HARD, shownAtSave)
        }
}
