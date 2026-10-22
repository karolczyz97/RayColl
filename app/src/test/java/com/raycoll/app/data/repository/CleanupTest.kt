package com.raycoll.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.firestore.Docs
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.firestore.WriteOp
import com.raycoll.app.domain.merge.guestHasData
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val repos = VmTestHarness.create(context, clock)
    private val paths = UserPaths("uid-1")
    private suspend fun deck(name: String) =
        repos.deckRepository.addDeckWithCards(name, listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))

    @Test fun `cleanup removes old tombstones and parent cards but keeps fresh data`() = runTest {
        val old = deck("Old")
        val fresh = deck("Fresh")
        val alive = deck("Alive")
        val oldCard = repos.deckRepository.getDeck(alive)!!.cards.single().id
        repos.store.write(WriteOp.Update(paths.deck(old), mapOf("deletedAt" to clock.now() - 31L * 86_400_000)))
        repos.store.write(WriteOp.Update(paths.deck(fresh), mapOf("deletedAt" to clock.now() - 86_400_000)))
        repos.store.write(
            WriteOp.Update(paths.card(alive, oldCard), mapOf("deletedAt" to clock.now() - 31L * 86_400_000))
        )
        Cleanup(repos.store, clock).run("uid-1")
        assertNull(repos.store.cachedDoc(paths.deck(old)))
        assertEquals(emptyList<Any>(), repos.store.cached(paths.cards(old)))
        assertNotNull(repos.store.cachedDoc(paths.deck(fresh)))
        assertNotNull(repos.store.cachedDoc(paths.deck(alive)))
        assertNull(repos.store.cachedDoc(paths.card(alive, oldCard)))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `archive expiry waits for a server response`() = runTest {
        val id = deck("Archived")
        repos.deckRepository.archiveDeck(id)
        clock.advance(ArchiveConstants.RETENTION_MS)
        val gate = MutableStateFlow(Docs(repos.store.cached(paths.decks()), true))
        val store = object : DocumentStore by repos.store {
            override fun observe(collectionPath: String) = if (collectionPath ==
                paths.decks()
            ) {
                gate
            } else {
                repos.store.observe(collectionPath)
            }
        }
        val job = launch { Cleanup(store, clock).run("uid-1") }
        runCurrent()
        assertNotNull(repos.deckRepository.getDeck(id))
        gate.value = gate.value.copy(fromCache = false)
        job.join()
        assertNull(repos.deckRepository.getDeck(id))
        assertEquals(clock.now(), repos.snapshot().groups.single().deletedAt)
    }

    @Test
    fun `purging a deleted seed card preserves the guest deck change`() = runTest {
        repos.seed()
        val seed = repos.snapshot().groups.first()
        val removed = seed.cards.first()
        repos.deckRepository.deleteCard(removed.id)
        clock.advance(31L * 86_400_000)

        Cleanup(repos.store, clock).run("uid-1")

        val snapshot = repos.snapshot()
        assertNull(repos.store.cachedDoc(paths.card(seed.id, removed.id)))
        assertEquals(clock.now(), snapshot.groups.first().updatedAt)
        assertTrue(guestHasData(snapshot.groups, snapshot.activityHeatmap, snapshot.studyModes))
        repos.account.signIn("google")
        repos.backupRepository.joinGuest(snapshot, "google", " (from phone)")
        assertEquals(listOf(seed.id), repos.snapshot().groups.map { it.id })
        assertEquals(seed.cards.drop(1), repos.snapshot().groups.single().cards)
    }

    @Test
    fun `deleted modes are purged only when deleted more than 30 days ago`() = runTest {
        val modes = repos.studyModeRepository
        val old = modes.addMode("Old", emptyList())
        val boundary = modes.addMode("Boundary", emptyList())
        val recent = modes.addMode("Recent", emptyList())
        val live = modes.addMode("Live", emptyList())
        val start = clock.now()
        modes.deleteMode(old)
        clock.advance(SrsConstants.DAY_MS)
        modes.deleteMode(boundary)
        clock.advance(SrsConstants.DAY_MS)
        modes.deleteMode(recent)
        // granica: usunięty dokładnie 30 dni temu jeszcze zostaje (kasowane tylko starsze)
        clock.current = start + SrsConstants.DAY_MS + 30 * SrsConstants.DAY_MS

        Cleanup(repos.store, clock).run("uid-1")

        assertNull(repos.store.cachedDoc(paths.mode(old)))
        assertNotNull(repos.store.cachedDoc(paths.mode(boundary)))
        assertNotNull(repos.store.cachedDoc(paths.mode(recent)))
        assertNotNull(repos.store.cachedDoc(paths.mode(live)))
    }
}
