package com.raycoll.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Oceny z sesji: zapis po kolei, potwierdzenie zakończenia i jawny błąd z ponowieniem. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewWriteQueueTest {

    @Test
    fun `immediate failure can be retried and immediate success can accept another review`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var fail = true
        var written = 0
        val queue = ReviewWriteQueue(scope) {
            if (fail) throw IOException("disk full")
            written++
        }
        try {
            queue.submit(PendingReview("deck", "card", 3, null))
            assertTrue(queue.state.value.failed)
            fail = false
            queue.retry()
            assertEquals(1, written)
            queue.submit(PendingReview("deck", "next", 3, null))
            assertEquals(2, written)
            assertEquals(0, queue.state.value.pending)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `retry keeps the namespace captured when a review was submitted`() = runTest {
        var namespace = "account-A"
        var fail = true
        val written = mutableListOf<PendingReview>()
        val queue = ReviewWriteQueue(backgroundScope, currentNamespace = { namespace }) { review ->
            if (fail) throw IOException("disk full")
            written += review
        }
        queue.submit(PendingReview("deck", "card", 3, null))
        runCurrent()
        assertTrue(queue.state.value.failed)
        namespace = NamespaceManager.GUEST
        fail = false
        queue.retry()
        runCurrent()
        assertEquals("account-A", written.single().namespace)
        assertTrue(queue.awaitIdle())
    }

    private val clock = FakeClock()
    private lateinit var repos: TestRepos
    private lateinit var deckId: String
    private lateinit var cardIds: List<String>

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        kotlinx.coroutines.runBlocking {
            deckId = repos.deckRepository.addDeckWithCards(
                "Deck",
                listOf("en-US", "pl-PL"),
                listOf("A", "B"),
                listOf(listOf("a1", "b1"), listOf("a2", "b2"), listOf("a3", "b3"))
            )
            cardIds = repos.deckRepository.getDeck(deckId)!!.cards.map { it.id }
        }
    }

    @After
    fun teardown() {
        repos.db.close()
    }

    private fun review(cardId: String, rating: Int = 3) = PendingReview(deckId, cardId, rating, null)

    @Test
    fun `awaitIdle returns only after every submitted review is in the database (slow DAO)`() = runTest {
        val written = mutableListOf<String>()
        val queue = ReviewWriteQueue(backgroundScope) { r ->
            delay(700) // celowo wolny zapis
            repos.deckRepository.reviewCard(r.deckId, r.cardId, r.rating, r.replaceFromBase)
            written += r.cardId
        }
        cardIds.forEach { queue.submit(review(it)) }
        assertEquals(3, queue.state.value.pending)

        val idle = async { queue.awaitIdle() }
        runCurrent()
        assertFalse("nie wolno ogłosić końca przed zapisem", idle.isCompleted)
        advanceUntilIdle()
        assertTrue(idle.await())

        assertEquals("kolejność zapisów", cardIds, written)
        val deck = repos.deckRepository.getDeck(deckId)!!
        assertTrue(deck.cards.all { it.srsState.repetitions == 1 })
        assertEquals(ReviewQueueState(pending = 0, failed = false), queue.state.value)
    }

    @Test
    fun `failed write is reported, keeps the order and is saved on retry`() = runTest {
        var failing = true
        val written = mutableListOf<String>()
        val queue = ReviewWriteQueue(backgroundScope) { r ->
            if (failing && r.cardId == cardIds[1]) throw IOException("disk full")
            repos.deckRepository.reviewCard(r.deckId, r.cardId, r.rating, r.replaceFromBase)
            written += r.cardId
        }
        cardIds.forEach { queue.submit(review(it)) }

        assertFalse("błąd zapisu nie może udawać sukcesu", queue.awaitIdle())
        assertEquals(ReviewQueueState(pending = 2, failed = true), queue.state.value)
        assertEquals(listOf(cardIds[0]), written)

        failing = false
        queue.retry()
        assertTrue(queue.awaitIdle())
        assertEquals(cardIds, written)
        assertTrue(repos.deckRepository.getDeck(deckId)!!.cards.all { it.srsState.repetitions == 1 })
    }

    @Test
    fun `a new rating after a failure retries the pending ones first`() = runTest {
        var failing = true
        val written = mutableListOf<String>()
        val queue = ReviewWriteQueue(backgroundScope) { r ->
            if (failing) throw IOException("busy")
            written += r.cardId
        }
        queue.submit(review(cardIds[0]))
        assertFalse(queue.awaitIdle())
        failing = false
        queue.submit(review(cardIds[1]))
        assertTrue(queue.awaitIdle())
        assertEquals(cardIds.take(2), written)
    }
}
