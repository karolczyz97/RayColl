package com.raycoll.app.ui.archive

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArchiveViewModelTest {

    @Test
    fun `open archive refreshes the countdown without a database mutation`() = runTest(dispatcher) {
        val id = repos.deckRepository.addDeckWithCards("Deck", listOf("en", "pl"), listOf("A", "B"), emptyList())
        repos.deckRepository.archiveDeck(id)
        val vm = vm()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val before = vm.decks { it.size == 1 }.single().daysLeft
        clock.advance(86_400_000L)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(before - 1, vm.decks { it.singleOrNull()?.daysLeft == before - 1 }.single().daysLeft)
        collector.cancel()
    }

    private val viewModels = mutableListOf<ArchiveViewModel>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var clock: FakeClock
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    }

    @After
    fun teardown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
        repos.db.close()
    }

    private fun vm() = ArchiveViewModel(repos.deckRepository, clock).also { viewModels += it }

    private suspend fun ArchiveViewModel.decks(predicate: (List<ArchivedDeck>) -> Boolean) =
        uiState.filterIsInstance<ArchiveUiState.Ready>().first { predicate(it.decks) }.decks

    @Test
    fun `archive lists decks with card count and countdown ordered by archival time`() = runTest(dispatcher) {
        val deck1Id = repos.deckRepository.addDeckWithCards(
            "Deck1",
            listOf("en", "pl"),
            listOf("P1", "P2"),
            emptyList()
        )
        val deck2Id = repos.deckRepository.addDeckWithCards(
            "Deck2",
            listOf("en", "pl"),
            listOf("P1", "P2"),
            emptyList()
        )
        repos.deckRepository.archiveDeck(deck1Id)
        clock.advance(10_000)
        repos.deckRepository.archiveDeck(deck2Id)

        val vm = vm()

        val items = vm.decks { it.size == 2 }
        assertEquals(2, items.size)
        assertEquals(deck2Id, items[0].id)
        assertEquals(deck1Id, items[1].id)
        assertTrue(items[0].daysLeft >= 0)
    }

    @Test
    fun `daysLeft liczony od wstrzykniętego zegara a nie czasu systemowego`() = runTest(dispatcher) {
        val baseTime = 1_700_000_000_000L
        clock.current = baseTime
        val deckId = repos.deckRepository.addDeckWithCards(
            "CountDeck",
            listOf("en", "pl"),
            listOf("P1", "P2"),
            emptyList()
        )
        repos.deckRepository.archiveDeck(deckId)

        val vm = vm()

        val item1 = vm.decks { it.isNotEmpty() }.first()
        assertEquals(ArchiveConstants.RETENTION_DAYS, item1.daysLeft)

        // Przesuwamy wstrzyknięty zegar o 10 dni — now staje się baseTime + 10 dni, więc daysLeft maleje do 4
        clock.advance(10L * SrsConstants.DAY_MS)
        val vm2 = vm()

        val item2 = vm2.decks { it.isNotEmpty() }.first()
        assertEquals(ArchiveConstants.RETENTION_DAYS - 10, item2.daysLeft)
    }

    @Test
    fun `restore przywraca zarchiwizowana talie`() = runTest(dispatcher) {
        val deckId = repos.deckRepository.addDeckWithCards(
            "ToRestore",
            listOf("en", "pl"),
            listOf("P1", "P2"),
            emptyList()
        )
        repos.deckRepository.archiveDeck(deckId)

        val vm = vm()
        assertEquals(1, vm.decks { it.isNotEmpty() }.size)

        vm.restore(deckId)

        val remaining = vm.decks { it.isEmpty() }
        assertEquals(0, remaining.size)
    }

    @Test
    fun `archived card count excludes deleted cards`() = runTest(dispatcher) {
        val deckId = repos.deckRepository.addDeckWithCards(
            "DeckWithCards",
            listOf("en", "pl"),
            listOf("P1", "P2"),
            emptyList()
        )
        repos.deckRepository.addCard(deckId, listOf("front1", "back1"))
        val card1 = repos.deckRepository.getDeck(deckId)!!.cards.single().id
        repos.deckRepository.addCard(deckId, listOf("front2", "back2"))
        repos.deckRepository.deleteCard(card1)
        repos.deckRepository.archiveDeck(deckId)

        val vm = vm()

        val item = vm.decks { it.isNotEmpty() }.first()
        assertEquals(1, item.cardCount)
    }
}
