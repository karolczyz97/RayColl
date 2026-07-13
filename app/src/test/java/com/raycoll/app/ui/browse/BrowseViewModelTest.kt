package com.raycoll.app.ui.browse

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.ui.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BrowseViewModelTest {

    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repos: com.raycoll.app.ui.TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), com.raycoll.app.ui.FakeClock())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        repos.db.close()
    }

    private suspend fun seedDeck(cardsCount: Int = 2, activePageCount: Int = 2): String {
        val cards = (1..cardsCount).map { i ->
            Flashcard(id = "c$i", pages = listOf("front$i", "back$i"), srsState = SrsState())
        }
        return repos.deckRepository.addDeckWithCards(
            "TestDeck", listOf("en-US", "pl-PL"), listOf("Front", "Back"), cards
        )
    }

    private fun vm() = BrowseViewModel(repos.deckRepository)

    @Test
    fun `loadDeck emituje karty`() = runTest(dispatcher) {
        val deckId = seedDeck(cardsCount = 3)
        val vm = vm()
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        assertEquals(3, vm.cards.value.size)
        assertNotNull(vm.deck.value)
        assertEquals("TestDeck", vm.deck.value?.name)
    }

    @Test
    fun `addCard emituje editRequest i ustawia liczbę stron z activePageCount`() = runTest(dispatcher) {
        val deckId = seedDeck(activePageCount = 3)
        // Zmień activePageCount na 3
        repos.deckRepository.setVisiblePageCount(deckId, 3)
        val vm = vm()
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        vm.addCard(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val editId = vm.editRequestCardId.first { it != null }
        // Nowa karta ma 3 puste strony (zgodnie z activePageCount)
        val newCard = vm.cards.first { it.any { c -> c.id == editId } }.find { it.id == editId }
        assertNotNull(newCard)
        assertEquals(3, newCard!!.pages.size)
        assertTrue(newCard.pages.all { it.isEmpty() })
    }

    @Test
    fun `consumeEditRequest czyści sygnał`() = runTest(dispatcher) {
        val deckId = seedDeck()
        val vm = vm()
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        vm.addCard(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        assertNotNull(vm.editRequestCardId.first { it != null })
        vm.consumeEditRequest()
        assertNull(vm.editRequestCardId.first { it == null })
    }

    @Test
    fun `updateCard zmienia zawartość karty`() = runTest(dispatcher) {
        val deckId = seedDeck()
        val vm = vm()
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val original = vm.cards.first { it.isNotEmpty() }.first()
        val updated = original.copy(pages = listOf("NEW", "NOWE"))
        vm.updateCard(deckId, updated)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val after = vm.cards.first { it.any { c -> c.id == original.id && c.pages == listOf("NEW", "NOWE") } }.find { it.id == original.id }
        assertEquals(listOf("NEW", "NOWE"), after?.pages)
    }

    @Test
    fun `deleteCard usuwa kartę z listy`() = runTest(dispatcher) {
        val deckId = seedDeck(cardsCount = 3)
        val vm = vm()
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val initial = vm.cards.first { it.isNotEmpty() }
        val initialCount = initial.size
        val toDelete = initial.first().id
        vm.deleteCard(deckId, toDelete)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val after = vm.cards.first { it.none { c -> c.id == toDelete } }
        assertEquals(initialCount - 1, after.size)
        assertTrue(after.none { it.id == toDelete })
    }

    @Test
    fun `loadDeck ponowne wołanie z tym samym id nie restartuje kolekcji`() = runTest(dispatcher) {
        val deckId = seedDeck()
        val vm = vm()
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val firstCard = vm.cards.first { it.isNotEmpty() }.first().id
        vm.loadDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        assertEquals(firstCard, vm.cards.first { it.isNotEmpty() }.first().id)
    }
}
