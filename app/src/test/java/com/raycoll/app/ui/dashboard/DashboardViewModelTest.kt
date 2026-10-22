package com.raycoll.app.ui.dashboard

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.MainDispatcherRule
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {
    @get:Rule(order = 1)
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @get:Rule(order = 0)
    val testPreferences = PreferencesRule()

    private val clock = FakeClock()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    }

    private fun vm() = mainDispatcher.track(
        DashboardViewModel(
            repos.deckRepository,
            repos.studyModeRepository,
            clock,
            testPreferences.repository,
            UnconfinedTestDispatcher()
        )
    )

    private suspend fun DashboardViewModel.ready(predicate: (DashboardUiState.Ready) -> Boolean = { true }) =
        uiState.filterIsInstance<DashboardUiState.Ready>().first(predicate)

    @Test
    fun `seeded decks and modes are listed`() = runTest {
        repos.seed("pl")
        val state = vm().ready { it.decks.size == 2 }
        assertEquals(SEED_MODES.map { it.id }, state.modes.map { it.id })
    }

    @Test
    fun `due count follows the injected clock`() = runTest {
        val deckId = repos.deckRepository.addDeckWithCards(
            "Deck",
            listOf("en-US", "pl-PL"),
            listOf("A", "B"),
            listOf(listOf("a", "b"))
        )
        repos.deckRepository.setStudyFilter(deckId, StudyFilter.NEW_AND_REVIEW)
        val vm = vm()
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, vm.ready { it.decks.isNotEmpty() }.decks.single().dueCount)
        val card = repos.deckRepository.getDeck(deckId)!!.cards.single()
        repos.deckRepository.reviewCard(deckId, card.id, 3)
        vm.ready { it.decks.single().dueCount == 0 }
        clock.current = repos.deckRepository.getDeck(deckId)!!.cards.single().srsState.nextReviewTimestamp + 1
        advanceTimeBy(60_001)
        assertEquals(1, vm.ready { it.decks.single().dueCount == 1 }.decks.single().dueCount)
        subscription.cancel()
    }

    @Test
    fun `archived deck leaves the list`() = runTest {
        repos.seed("en")
        val vm = vm()
        val deckId = vm.ready { it.decks.size == 2 }.decks.first().id
        vm.archiveDeck(deckId)
        assertEquals(1, vm.ready { it.decks.size == 1 }.decks.size)
    }

    @Test
    fun `undo after archiving restores the deck in the list`() = runTest {
        repos.seed("en")
        val vm = vm()
        val decks = vm.ready { it.decks.size == 2 }.decks.map { it.id }
        vm.archiveDeck(decks.first())
        vm.ready { it.decks.size == 1 }

        vm.restoreDeck(decks.first())

        assertEquals(decks, vm.ready { it.decks.size == 2 }.decks.map { it.id })
        assertEquals(null, repos.deckRepository.getDeck(decks.first())!!.archivedAt)
    }

    @Test
    fun `reordered decks keep their order in a new view model`() = runTest {
        repos.seed("en")
        val vm = vm()
        val order = vm.ready { it.decks.size == 2 }.decks.map { it.id }.reversed()

        vm.reorderDecks(order)

        assertEquals(order, vm.ready { it.decks.map { deck -> deck.id } == order }.decks.map { it.id })
        assertEquals(order, vm().ready { it.decks.size == 2 }.decks.map { it.id })
    }

    @Test
    fun `failed reorder reports a save failure and preserves the stored order`() = runTest {
        repos.seed("en")
        val vm = vm()
        val order = vm.ready { it.decks.size == 2 }.decks.map { it.id }
        repos.store.failWhen = { true }

        vm.reorderDecks(order.reversed())

        assertEquals(DashboardEvent.SaveFailed, vm.events.first())
        assertEquals(order, vm().ready { it.decks.size == 2 }.decks.map { it.id })
    }
}
