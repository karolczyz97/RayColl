package com.raycoll.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckReorderTest {
    private val clock = FakeClock()
    private val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    private val paths = UserPaths(checkNotNull(repos.account.uid.value))

    @Test
    fun `reordering saves positions and timestamps while preserving deck data`() = runTest {
        repos.put(StoreData(groups = listOf(deck("a"), deck("b"), deck("c"))))
        val before = repos.deckRepository.getDeck("a")!!
        clock.advance(1_000)

        repos.deckRepository.reorderDecks(listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), repos.deckRepository.activeDecks().first().map { it.id })
        assertEquals(before.copy(updatedAt = clock.now()), repos.deckRepository.getDeck("a"))
        listOf("c", "a", "b").forEachIndexed { position, id ->
            val doc = repos.store.cachedDoc(paths.deck(id))!!
            assertEquals(position.toLong(), FirestoreSchema.positionFromMap(doc.data))
            assertEquals(clock.now(), doc.data[FirestoreSchema.FIELD_UPDATED_AT])
        }
    }

    @Test
    fun `stale order ignores archived deleted and missing decks and keeps new decks`() = runTest {
        repos.put(StoreData(groups = listOf(deck("a"), deck("b"), deck("c"), deck("d"), deck("new"))))
        repos.deckRepository.archiveDeck("b")
        repos.deckRepository.deleteDeck("d")
        val archived = repos.store.cachedDoc(paths.deck("b"))
        val deleted = repos.store.cachedDoc(paths.deck("d"))

        repos.deckRepository.reorderDecks(listOf("c", "missing", "b", "d", "a", "c"))

        assertEquals(listOf("c", "a", "new"), repos.deckRepository.activeDecks().first().map { it.id })
        assertEquals(archived, repos.store.cachedDoc(paths.deck("b")))
        assertEquals(deleted, repos.store.cachedDoc(paths.deck("d")))
        assertNull(repos.store.cachedDoc(paths.deck("missing")))
    }

    @Test
    fun `dropping in the same place does not write or change timestamps`() = runTest {
        repos.put(StoreData(groups = listOf(deck("a"), deck("b"))))
        val before = repos.store.cached(paths.decks())
        clock.advance(1_000)
        repos.store.failWhen = { true }

        repos.deckRepository.reorderDecks(listOf("a", "b"))
        repos.deckRepository.reorderDecks(emptyList())

        assertEquals(before, repos.store.cached(paths.decks()))
    }

    private fun deck(id: String) = FlashcardGroup(id, "Deck $id", emptyList(), updatedAt = clock.now())
}
