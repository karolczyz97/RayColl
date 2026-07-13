package com.raycoll.app.ui.dashboard

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.domain.model.FlashcardGroup
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.arch.core.executor.testing.InstantTaskExecutorRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DashboardViewModelTest {

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

    private fun vm() = DashboardViewModel(repos.deckRepository, repos.studyModeRepository).also {
        // init { viewModelScope.launch { seedIfEmpty } } — VM odpala to na Main dispatcherze
    }

    @Test
    fun `seedIfEmpty przy starcie tworzy 2 talie i 2 tryby`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val decks = vm.decks.first { it.isNotEmpty() }
        val modes = vm.modes.first { it.isNotEmpty() }
        assertEquals(2, decks.size)
        assertEquals(2, modes.size)
        assertTrue(decks.any { it.name == "Angielski - Podstawy" })
        assertTrue(modes.any { it.id == "classic" })
        assertTrue(modes.any { it.id == "listen-speak" })
    }

    @Test
    fun `setActiveMode zmienia aktywny tryb talii`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val deckId = vm.decks.first { it.isNotEmpty() }.first().id
        vm.setActiveMode(deckId, "listen-speak")
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val updated = repos.deckRepository.getDeck(deckId)
        assertEquals("listen-speak", updated?.activeModeId)
    }

    @Test
    fun `archiveDeck przenosi talię z activeDecks`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val seeded = vm.decks.first { it.isNotEmpty() }
        val initialCount = seeded.size
        val deckId = seeded.first().id
        vm.archiveDeck(deckId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val after = vm.decks.first()
        assertEquals(initialCount - 1, after.size)
        assertTrue(after.none { it.id == deckId })
    }

    @Test
    fun `filtr żywych trybów — usunięty tryb nie pojawia się na liście`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val modeId = vm.modes.first { it.isNotEmpty() }.first().id
        repos.studyModeRepository.deleteMode(modeId)
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        assertTrue(vm.modes.first().none { it.id == modeId })
    }

    @Test
    fun `seedIfEmpty nie dubluje przy ponownym starcie`() = runTest(dispatcher) {
        val vm1 = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val count1 = vm1.decks.value.size
        val vm2 = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        assertEquals(count1, vm2.decks.value.size)
    }
}
