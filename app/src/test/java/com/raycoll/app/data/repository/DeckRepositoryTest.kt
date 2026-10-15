package com.raycoll.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.srs.calculateFsrs
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeckRepositoryTest {

    @Test
    fun `unchanged deck metadata does not advance its edit timestamp`() = runTest {
        val id = seedDeckWithCard()
        val before = repos.deckRepository.getDeck(id)!!
        clock.advance(1_000)
        repos.deckRepository.renameDeck(id, "  ${before.name}  ")
        repos.deckRepository.setPageName(id, 0, before.pageNames[0])
        repos.deckRepository.setPageLanguage(id, 0, before.pageLanguages[0])
        repos.deckRepository.setActivePageCount(id, before.activePageCount)
        repos.deckRepository.setActiveStudyMode(id, before.activeModeId)
        repos.deckRepository.setStudyFilter(id, before.studyFilter)
        repos.deckRepository.setCardOrder(id, before.cardOrder)
        assertEquals(before, repos.deckRepository.getDeck(id))
        repos.deckRepository.setPageName(id, 0, "Changed")
        assertEquals(clock.now(), repos.deckRepository.getDeck(id)!!.updatedAt)
    }

    @Test
    fun `archive restore and repeated deletion cannot revive or advance a tombstone`() = runTest {
        val id = seedDeckWithCard()
        repos.deckRepository.deleteDeck(id)
        val before = repos.localStore.snapshot().groups.single()
        clock.advance(1_000)
        repos.deckRepository.archiveDeck(id)
        repos.deckRepository.restoreDeck(id)
        repos.deckRepository.deleteDeck(id)
        assertEquals(before, repos.localStore.snapshot().groups.single())
    }

    @Test
    fun `reviewing a deleted card neither revives it nor adds activity`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        val cardId = repos.deckRepository.getDeck(deckId)!!.cards.single().id
        repos.deckRepository.deleteCard(cardId)
        val before = repos.localStore.snapshot().groups.single().cards.single()
        clock.advance(1_000)
        repos.deckRepository.reviewCard(deckId, cardId, 3)
        val after = repos.localStore.snapshot()
        assertEquals(before, after.groups.single().cards.single())
        assertTrue(after.activityHeatmap.isEmpty())
    }

    @Test
    fun `swapping a new page also moves existing two page cards`() = runTest {
        val id = seedDeckWithCard()
        val before = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.setActivePageCount(id, 3)
        repos.deckRepository.swapPages(id, 1, 2)
        val after = repos.deckRepository.getDeck(id)!!.cards.single()
        assertEquals(listOf(before.pages[0], "", before.pages[1]), after.pages)
        assertEquals(clock.now(), after.contentUpdatedAt)
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        repos.db.close()
    }

    private suspend fun seedDeckWithCard(): String = repos.deckRepository.addDeckWithCards(
        "Deck",
        listOf("en-US", "pl-PL"),
        listOf("Front", "Back"),
        listOf(listOf("hi", "cześć"))
    )

    @Test
    fun `reviewCard zapisuje heatmapę od pustej (pierwsza ocena dnia nie ginie)`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        val today = LocalDates.dayKey(clock.now())

        // Regresja: incrementActivity to UPDATE — bez utworzenia wiersza pierwsza ocena dnia
        // gubiłaby się i heatmapa/streak zostałyby puste na urządzeniu.
        assertNull(repos.localStore.snapshot().activityHeatmap[today])
        repos.deckRepository.reviewCard(deckId, repos.deckRepository.getDeck(deckId)!!.cards.single().id, 3)
        assertEquals(1, repos.localStore.snapshot().activityHeatmap[today])
        repos.deckRepository.reviewCard(deckId, repos.deckRepository.getDeck(deckId)!!.cards.single().id, 3)
        assertEquals(2, repos.localStore.snapshot().activityHeatmap[today])
    }

    @Test
    fun `reviewCard aktualizuje SRS karty i stempluje srsUpdatedAt`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        repos.deckRepository.reviewCard(deckId, repos.deckRepository.getDeck(deckId)!!.cards.single().id, 3)
        val card = repos.deckRepository.getDeck(deckId)!!.cards.single()
        assertEquals(1, card.srsState.repetitions)
        assertEquals(clock.now(), card.srsUpdatedAt)
    }

    @Test
    fun `snapshot do synchronizacji zawiera tombstone usuniętej karty`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        repos.deckRepository.deleteCard(repos.deckRepository.getDeck(deckId)!!.cards.single().id)

        // Regresja: bez tombstone'a usunięcie nie trafia do chmury, a inne urządzenie
        // przy merge'u przywraca kartę. Ekrany nadal widzą tylko żywe karty.
        val synced = repos.localStore.snapshot().groups.single().cards.single()
        assertNotNull(synced.deletedAt)
        assertEquals(emptyList<Flashcard>(), repos.deckRepository.getDeck(deckId)!!.cards)
    }

    @Test
    fun `activeDecks emituje po dodaniu karty`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        awaitEmissions(repos.deckRepository.activeDecks()) { emissions ->
            awaitValue(emissions) { decks -> decks.single().cards.size == 1 }
            repos.deckRepository.addCard(deckId, listOf("bye", "pa"))
            awaitValue(emissions) { decks -> decks.single().cards.size == 2 }
        }
    }

    @Test
    fun `activeDecks przełącza się na dane nowego konta`() = runTest(dispatcher) {
        seedDeckWithCard()
        awaitEmissions(repos.deckRepository.activeDecks()) { emissions ->
            awaitValue(emissions) { decks -> decks.size == 1 }
            repos.namespaceManager.switchTo("uid-1")
            awaitValue(emissions) { decks -> decks.isEmpty() }
            repos.namespaceManager.switchToGuest()
            awaitValue(emissions) { decks -> decks.size == 1 }
        }
    }

    @Test
    fun `rename preserves live and tombstoned cards without rewriting them`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        repos.deckRepository.addCard(id, listOf("deleted", "usunięta"))
        val deletedId = repos.deckRepository.getDeck(id)!!.cards.last().id
        repos.deckRepository.deleteCard(deletedId)
        val before = repos.localStore.snapshot().groups.single().cards
        clock.advance(1_000)
        repos.deckRepository.renameDeck(id, " Renamed ")
        val after = repos.localStore.snapshot().groups.single()
        assertEquals("Renamed", after.name)
        assertEquals(clock.now(), after.updatedAt)
        assertEquals(before, after.cards)
    }

    @Test
    fun `editing a card stamps content time and preserves SRS`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        val original = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.reviewCard(id, original.id, 3)
        val reviewed = repos.deckRepository.getDeck(id)!!.cards.single()
        clock.advance(1_000)
        assertTrue(repos.deckRepository.updateCardPages(original.id, listOf("changed", "zmieniona")))
        val edited = repos.deckRepository.getDeck(id)!!.cards.single()
        assertEquals(listOf("changed", "zmieniona"), edited.pages)
        assertEquals(clock.now(), edited.contentUpdatedAt)
        assertEquals(reviewed.srsState, edited.srsState)
        assertEquals(reviewed.srsUpdatedAt, edited.srsUpdatedAt)
    }

    @Test
    fun `text edit and SRS review interleaved keep both, in either order`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        val card = repos.deckRepository.getDeck(id)!!.cards.single()
        // ekran edycji trzyma kartę sprzed oceny; zapis edycji nie może cofnąć oceny
        repos.deckRepository.reviewCard(id, card.id, 3)
        repos.deckRepository.updateCardPages(card.id, listOf("edit-1", "edycja-1"))
        var stored = repos.deckRepository.getDeck(id)!!.cards.single()
        assertEquals(listOf("edit-1", "edycja-1"), stored.pages)
        assertEquals(1, stored.srsState.repetitions)

        repos.deckRepository.updateCardPages(card.id, listOf("edit-2", "edycja-2"))
        repos.deckRepository.reviewCard(id, card.id, 3)
        stored = repos.deckRepository.getDeck(id)!!.cards.single()
        assertEquals(listOf("edit-2", "edycja-2"), stored.pages)
        assertEquals(2, stored.srsState.repetitions)

        // równolegle: oba zapisy się kończą w dowolnej kolejności
        coroutineScope {
            launch(Dispatchers.Default) { repos.deckRepository.reviewCard(id, card.id, 3) }
            launch(Dispatchers.Default) { repos.deckRepository.updateCardPages(card.id, listOf("edit-3", "edycja-3")) }
        }
        stored = repos.deckRepository.getDeck(id)!!.cards.single()
        assertEquals(listOf("edit-3", "edycja-3"), stored.pages)
        assertEquals(3, stored.srsState.repetitions)
    }

    @Test
    fun `editing keeps hidden pages, deck and tombstone fields`() = runTest(dispatcher) {
        val id = repos.deckRepository.addDeckWithCards(
            "Three pages",
            listOf("en-US", "pl-PL", "de-DE"),
            listOf("A", "B", "C"),
            listOf(listOf("a", "b", "hidden"))
        )
        repos.deckRepository.setActivePageCount(id, 2)
        val card = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.updateCardPages(card.id, listOf("A!", "B!"))
        val stored = repos.localStore.snapshot().groups.single().cards.single()
        assertEquals(listOf("A!", "B!", "hidden"), stored.pages)
        assertNull(stored.deletedAt)
    }

    @Test
    fun `editing a deleted or missing card writes nothing and reports it`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        val card = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.deleteCard(card.id)
        org.junit.Assert.assertFalse(repos.deckRepository.updateCardPages(card.id, listOf("x", "y")))
        org.junit.Assert.assertFalse(repos.deckRepository.updateCardPages("no-such-card", listOf("x", "y")))
        val tombstone = repos.localStore.snapshot().groups.single().cards.single()
        assertEquals(listOf("hi", "cześć"), tombstone.pages)
        assertNotNull(tombstone.deletedAt)
    }

    @Test
    fun `overlapping name and mode edits keep both values`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        repeat(20) { i ->
            coroutineScope {
                launch(Dispatchers.Default) { repos.deckRepository.renameDeck(id, "Name $i") }
                launch(Dispatchers.Default) { repos.deckRepository.setActiveStudyMode(id, "mode-$i") }
                launch(Dispatchers.Default) { repos.deckRepository.setPageName(id, i % 2, "Page $i") }
                launch(Dispatchers.Default) { repos.deckRepository.setPageLanguage(id, (i + 1) % 2, "de-DE") }
            }
            val deck = repos.deckRepository.getDeck(id)!!
            assertEquals("Name $i", deck.name)
            assertEquals("mode-$i", deck.activeModeId)
            assertEquals("Page $i", deck.pageNames[i % 2])
            assertEquals("de-DE", deck.pageLanguages[(i + 1) % 2])
        }
        // karty i ich postęp nietknięte
        assertEquals(listOf("hi", "cześć"), repos.deckRepository.getDeck(id)!!.cards.single().pages)
    }

    @Test
    fun `settings edits stamp updatedAt and signal sync once per change`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        assertEquals(CloudWriteMode.IMMEDIATE, repos.mutations.events.first())
        clock.advance(5_000)
        repos.deckRepository.setCardOrder(id, CardOrder.RANDOM)
        repos.deckRepository.setStudyFilter(id, StudyFilter.ALL)
        val deck = repos.deckRepository.getDeck(id)!!
        assertEquals(CardOrder.RANDOM, deck.cardOrder)
        assertEquals(StudyFilter.ALL, deck.studyFilter)
        assertEquals(clock.now(), deck.updatedAt)
        assertEquals(
            listOf(CloudWriteMode.DEBOUNCED, CloudWriteMode.DEBOUNCED),
            repos.mutations.events.take(2).toList()
        )
    }

    @Test
    fun `editing a deleted deck changes nothing`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        repos.deckRepository.deleteDeck(id)
        repos.deckRepository.renameDeck(id, "Ghost")
        repos.deckRepository.setPageName(id, 0, "Ghost")
        val tombstone = repos.localStore.snapshot().groups.single()
        assertEquals("Deck", tombstone.name)
        assertEquals(listOf("Front", "Back"), tombstone.pageNames)
    }

    @Test
    fun `page name is trimmed like the deck name`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        repos.deckRepository.setPageName(id, 0, "  Pytanie  ")
        assertEquals(listOf("Pytanie", "Back"), repos.deckRepository.getDeck(id)!!.pageNames)
    }

    @Test
    fun `page edits outside the page range change nothing`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        val before = repos.deckRepository.getDeck(id)!!
        clock.advance(5_000)
        repos.deckRepository.setPageName(id, 7, "Ghost")
        repos.deckRepository.setPageName(id, -1, "Ghost")
        repos.deckRepository.setPageLanguage(id, 7, "de-DE")
        assertEquals(before, repos.deckRepository.getDeck(id))
    }

    @Test
    fun `rerating uses the original SRS without counting another daily review`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        val original = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.reviewCard(id, original.id, 1)
        repos.deckRepository.reviewCard(id, original.id, 4, baseState = original.srsState)
        val reviewed = repos.deckRepository.getDeck(id)!!.cards.single()
        assertEquals(calculateFsrs(original.srsState, 4, clock.now()), reviewed.srsState)
        assertEquals(1, repos.localStore.snapshot().activityHeatmap[LocalDates.dayKey(clock.now())])
    }

    @Test
    fun `archive and restore preserve deck cards`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        repos.deckRepository.archiveDeck(id)
        assertEquals(clock.now(), repos.deckRepository.getDeck(id)!!.archivedAt)
        repos.deckRepository.restoreDeck(id)
        val restored = repos.deckRepository.getDeck(id)!!
        assertNull(restored.archivedAt)
        assertEquals(1, restored.cards.size)
    }

    @Test
    fun `purge only deletes archives past the retention period`() = runTest(dispatcher) {
        val expiredId = seedDeckWithCard()
        repos.deckRepository.archiveDeck(expiredId)
        clock.advance(ArchiveConstants.RETENTION_MS)
        val activeId = seedDeckWithCard()
        val recentId = seedDeckWithCard()
        repos.deckRepository.archiveDeck(recentId)
        repos.deckRepository.purgeExpiredArchives()
        val stored = repos.localStore.snapshot().groups.associateBy { it.id }
        assertEquals(clock.now(), stored.getValue(expiredId).deletedAt)
        assertNull(repos.deckRepository.getDeck(expiredId))
        assertNull(repos.deckRepository.getDeck(activeId)!!.deletedAt)
        assertNull(repos.deckRepository.getDeck(recentId)!!.deletedAt)
    }

    @Test
    fun `reducing active pages preserves hidden card content`() = runTest(dispatcher) {
        val id = repos.deckRepository.addDeckWithCards(
            "Three pages",
            listOf("en-US", "pl-PL", "de-DE"),
            listOf("A", "B", "C"),
            listOf(listOf("a", "b", "c"))
        )
        repos.deckRepository.setActivePageCount(id, 2)
        assertEquals(listOf("a", "b", "c"), repos.deckRepository.getDeck(id)!!.cards.single().pages)
        repos.deckRepository.setActivePageCount(id, 3)
        val restored = repos.deckRepository.getDeck(id)!!
        assertEquals(3, restored.activePageCount)
        assertEquals(listOf("A", "B", "C"), restored.pageNames)
    }

    @Test
    fun `merging an empty local store preserves remote cards before upload`() = runTest(dispatcher) {
        val id = seedDeckWithCard()
        val cloud = repos.localStore.snapshot()
        repos.localStore.replaceAll(StoreData(), notifySync = false)
        val merged = repos.localStore.mergeWithCloud(cloud)
        assertEquals(cloud, merged)
        assertEquals(cloud, repos.localStore.snapshot())
        assertTrue(repos.deckRepository.getDeck(id)!!.cards.isNotEmpty())
    }

    // Room emituje z własnych wątków — czekamy na wartości w realnym czasie (z limitem)
    private suspend fun <T> TestScope.awaitEmissions(flow: Flow<T>, block: suspend (Channel<T>) -> Unit) {
        val emissions = Channel<T>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { flow.collect { emissions.send(it) } }
        try {
            withContext(Dispatchers.Default) { withTimeout(5_000) { block(emissions) } }
        } finally {
            job.cancel()
        }
    }

    private suspend fun <T> awaitValue(emissions: Channel<T>, predicate: (T) -> Boolean) {
        while (!predicate(emissions.receive())) {
            // Pomiń wcześniejsze emisje i zaczekaj na zmianę sprawdzaną przez test.
        }
    }
}
