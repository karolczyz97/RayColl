package com.raycoll.app.ui.dashboard

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {
    private val clock = FakeClock()
    private val viewModels = mutableListOf<DashboardViewModel>()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    }

    @After
    fun teardown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
        repos.db.close()
    }

    private fun vm() = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock).also {
        viewModels +=
            it
    }

    private suspend fun DashboardViewModel.ready(predicate: (DashboardUiState.Ready) -> Boolean = { true }) =
        uiState.filterIsInstance<DashboardUiState.Ready>().first(predicate)

    @Test
    fun `seeded decks and modes are listed`() = runTest {
        repos.localStore.seedIfEmpty("pl")
        val state = vm().ready { it.decks.size == 2 }
        assertEquals(2, state.modes.size)
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
        assertEquals(1, vm.ready { it.decks.isNotEmpty() }.decks.single().dueCount)
    }

    @Test
    fun `archived deck leaves the list`() = runTest {
        repos.localStore.seedIfEmpty("en")
        val vm = vm()
        val deckId = vm.ready { it.decks.size == 2 }.decks.first().id
        vm.archiveDeck(deckId)
        assertEquals(1, vm.ready { it.decks.size == 1 }.decks.size)
    }
}
