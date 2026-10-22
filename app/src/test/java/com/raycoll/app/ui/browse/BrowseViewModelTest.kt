package com.raycoll.app.ui.browse

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.MainDispatcherRule
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BrowseViewModelTest {
    @get:Rule(order = 1)
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @get:Rule(order = 0)
    val testPreferences = PreferencesRule()

    @Test
    fun `adding a hidden card confirms the addition without clearing filters`() = runTest {
        val vm = vm(seedDeck(cards = 1))
        vm.ready()
        vm.setSearch("front")
        vm.setCategory(SrsCardCategory.REVIEW)
        vm.ready { it.category == SrsCardCategory.REVIEW }
        vm.addCard(listOf("hidden", "ukryta"))
        assertEquals(BrowseEvent.CardAdded, vm.events.first())
        val state = vm.ready { it.allCards.size == 2 }
        assertEquals("front", vm.search.value)
        assertEquals(SrsCardCategory.REVIEW, state.category)
        assertTrue(state.cards.isEmpty())
        assertFalse(state.allCards.isEmpty())
    }
    private val clock = FakeClock()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    }

    private suspend fun seedDeck(cards: Int): String = repos.deckRepository.addDeckWithCards(
        "TestDeck",
        listOf("en-US", "pl-PL"),
        listOf("Front", "Back"),
        (1..cards).map { listOf("front$it", "back$it") }
    )

    private fun vm(deckId: String) =
        mainDispatcher.track(BrowseViewModel(deckId, repos.deckRepository, testPreferences.repository))

    private suspend fun BrowseViewModel.ready(predicate: (BrowseUiState.Ready) -> Boolean = { true }) =
        uiState.filterIsInstance<BrowseUiState.Ready>().first(predicate)

    @Test
    fun `shows deck cards and counts`() = runTest {
        val vm = vm(seedDeck(cards = 3))

        val state = vm.ready { it.cards.size == 3 }
        assertEquals("TestDeck", state.deckName)
        assertEquals(3, state.counts.newCount)
    }

    @Test
    fun `missing deck is reported`() = runTest {
        val vm = vm("no-such-deck")

        assertEquals(BrowseUiState.DeckMissing, vm.uiState.first { it == BrowseUiState.DeckMissing })
    }

    @Test
    fun `soft-deleted deck is not shown`() = runTest {
        val id = seedDeck(cards = 1)
        val vm = vm(id)
        vm.ready()
        repos.deckRepository.deleteDeck(id)
        assertEquals(BrowseUiState.DeckMissing, vm.uiState.first { it == BrowseUiState.DeckMissing })
        // synchronizacja nadal zna usunięcie
        assertNotNull(repos.snapshot().groups.single().deletedAt)
    }

    @Test
    fun `added card appears with the given pages`() = runTest {
        val vm = vm(seedDeck(cards = 1))
        vm.ready { it.cards.size == 1 }

        vm.addCard(listOf("new", "nowa"))

        val added = vm.ready { it.cards.size == 2 }.cards.last()
        assertEquals(listOf("new", "nowa"), added.pages)
    }

    @Test
    fun `edited card keeps id and gets new pages`() = runTest {
        val vm = vm(seedDeck(cards = 2))
        val original = vm.ready { it.cards.size == 2 }.cards.first()

        vm.updateCard(original.id, listOf("NEW", "NOWE"))

        val edited = vm.ready { state -> state.cards.any { it.pages == listOf("NEW", "NOWE") } }.cards.first()
        assertEquals(original.id, edited.id)
    }

    @Test
    fun `editing a card deleted in the meantime reports it instead of saving`() = runTest {
        val vm = vm(seedDeck(cards = 2))
        val card = vm.ready { it.cards.size == 2 }.cards.first()
        repos.deckRepository.deleteCard(card.id)

        vm.updateCard(card.id, listOf("NEW", "NOWE"))

        assertEquals(BrowseEvent.CardMissing, vm.events.first())
        org.junit.Assert.assertFalse(vm.ready { it.cards.size == 1 }.cards.any { it.pages == listOf("NEW", "NOWE") })
    }

    @Test
    fun `deleted card disappears`() = runTest {
        val vm = vm(seedDeck(cards = 3))
        val toDelete = vm.ready { it.cards.size == 3 }.cards.first().id

        vm.deleteCard(toDelete)

        val state = vm.ready { it.cards.size == 2 }
        org.junit.Assert.assertFalse(state.cards.any { it.id == toDelete })
    }

    @Test
    fun `search filters visible pages`() = runTest {
        val vm = vm(seedDeck(cards = 3))
        vm.ready { it.cards.size == 3 }

        vm.setSearch("front2")
        assertEquals("front2", vm.search.value)

        assertEquals(listOf("front2"), vm.ready { it.cards.size == 1 }.cards.map { it.pages.first() })
    }

    @Test
    fun `search updates immediately without waiting for list filtering`() = runTest {
        val vm = vm(seedDeck(cards = 3))
        for (query in listOf("k", "ki", "kit", "kitc", "kitch", "kitche", "kitchen")) {
            vm.setSearch(query)
            assertEquals(query, vm.search.value)
        }
    }

    @Test
    fun `category filter keeps counts of the whole deck`() = runTest {
        val vm = vm(seedDeck(cards = 3))
        vm.ready { it.cards.size == 3 }

        vm.setCategory(SrsCardCategory.MASTERED)

        val state = vm.ready { it.category == SrsCardCategory.MASTERED }
        assertEquals(0, state.cards.size)
        assertEquals(3, state.counts.total)
    }
}
