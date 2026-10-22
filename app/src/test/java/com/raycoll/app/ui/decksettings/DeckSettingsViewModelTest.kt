package com.raycoll.app.ui.decksettings

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.MainDispatcherRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeckSettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
    }

    private suspend fun seedDeck() = repos.deckRepository.addDeckWithCards(
        "TestDeck",
        listOf("en-US", "pl-PL"),
        listOf("Front", "Back"),
        listOf(listOf("hello", "cześć"))
    )
    private fun vm(deckId: String) =
        mainDispatcher.track(DeckSettingsViewModel(deckId, repos.deckRepository, repos.studyModeRepository))
    private suspend fun DeckSettingsViewModel.ready(predicate: (DeckSettingsUiState.Ready) -> Boolean = { true }) =
        uiState.filterIsInstance<DeckSettingsUiState.Ready>().first(predicate)

    @Test
    fun `route loads the selected deck`() = runTest {
        assertEquals("TestDeck", vm(seedDeck()).ready().deck.name)
    }

    @Test
    fun `rename persists and updates the screen`() = runTest {
        val id = seedDeck()
        val vm = vm(id)
        vm.ready()
        vm.rename("Nowa Nazwa")
        assertEquals("Nowa Nazwa", vm.ready { it.deck.name == "Nowa Nazwa" }.deck.name)
        assertEquals("Nowa Nazwa", repos.deckRepository.getDeck(id)!!.name)
    }

    @Test
    fun `page name is persisted and observed`() = runTest {
        val vm = vm(seedDeck())
        vm.ready()
        vm.setPageName(0, "Phrase")
        assertEquals("Phrase", vm.ready { it.deck.pageNames[0] == "Phrase" }.deck.pageNames[0])
    }

    @Test
    fun `page language is persisted and observed`() = runTest {
        val vm = vm(seedDeck())
        vm.ready()
        vm.setPageLanguage(1, "es-ES")
        assertEquals("es-ES", vm.ready { it.deck.pageLanguages[1] == "es-ES" }.deck.pageLanguages[1])
    }

    @Test
    fun `moving a page updates metadata and card contents`() = runTest {
        val vm = vm(seedDeck())
        vm.ready()
        vm.movePage(0, 1)
        val deck = vm.ready {
            it.deck.pageNames == listOf("Back", "Front") &&
                it.deck.cards.single().pages == listOf("cześć", "hello")
        }.deck
        assertEquals(listOf("pl-PL", "en-US"), deck.pageLanguages)
        assertEquals(listOf("cześć", "hello"), deck.cards.single().pages)
    }

    @Test
    fun `increasing page count updates padded metadata`() = runTest {
        val vm = vm(seedDeck())
        vm.ready()
        vm.setActivePageCount(4) { "Strona ${it + 1}" }
        val deck = vm.ready { it.deck.activePageCount == 4 }.deck
        assertEquals(listOf("Front", "Back", "Strona 3", "Strona 4"), deck.pageNames)
        vm.setActivePageCount(2) { "Seite ${it + 1}" }
        vm.ready { it.deck.activePageCount == 2 }
        vm.setActivePageCount(4) { "Seite ${it + 1}" }
        assertEquals(deck.pageNames, vm.ready { it.deck.activePageCount == 4 }.deck.pageNames)
    }

    @Test
    fun `active mode selection persists`() = runTest {
        val vm = vm(seedDeck())
        vm.ready()
        vm.setActiveMode("listen-speak")
        assertEquals("listen-speak", vm.ready { it.deck.activeModeId == "listen-speak" }.deck.activeModeId)
    }

    @Test
    fun `archiving persists and emits Closed`() = runTest {
        val id = seedDeck()
        val vm = vm(id)
        vm.archive()
        assertEquals(DeckSettingsEvent.Archived(id), vm.events.first())
        assertNotNull(repos.deckRepository.getDeck(id)!!.archivedAt)
    }

    @Test
    fun `deleting persists tombstone and emits Closed`() = runTest {
        val id = seedDeck()
        val vm = vm(id)
        vm.delete()
        assertEquals(DeckSettingsEvent.Closed, vm.events.first())
        assertNotNull(repos.snapshot().groups.single().deletedAt)
        org.junit.Assert.assertNull(repos.deckRepository.getDeck(id))
    }

    @Test
    fun `deck deleted elsewhere is reported as missing`() = runTest {
        val id = seedDeck()
        val vm = vm(id)
        vm.ready()
        repos.deckRepository.deleteDeck(id)
        assertEquals(DeckSettingsUiState.DeckMissing, vm.uiState.first { it is DeckSettingsUiState.DeckMissing })
    }

    @Test
    fun `deleting from this screen keeps the last view until it closes`() = runTest {
        val id = seedDeck()
        val vm = vm(id)
        vm.ready()
        vm.delete()
        assertEquals(DeckSettingsEvent.Closed, vm.events.first())
        assertTrue(vm.uiState.value is DeckSettingsUiState.Ready)
    }

    @Test
    fun `deleting a missing deck is an idempotent success`() = runTest {
        val vm = vm("nonexistent")
        vm.delete()
        assertEquals(DeckSettingsEvent.Closed, vm.events.first())
        assertTrue(repos.snapshot().groups.isEmpty())
    }
}
