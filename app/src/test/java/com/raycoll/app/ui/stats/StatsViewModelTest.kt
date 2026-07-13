package com.raycoll.app.ui.stats

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
class StatsViewModelTest {

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

    private fun vm() = StatsViewModel(repos.deckRepository)

    @Test
    fun `pusty stan przy braku talii`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val s = vm.stats.first()
        assertEquals(0, s.totalCards)
        assertEquals(0, s.streak)
        assertTrue(s.decks.isEmpty())
    }

    @Test
    fun `stats liczy karty z wielu talii`() = runTest(dispatcher) {
        repos.deckRepository.addDeckWithCards(
            "A", listOf("en", "pl"), listOf("F", "B"),
            listOf(
                Flashcard(id = "a1", pages = listOf("x", "y"), srsState = SrsState(state = 0)),
                Flashcard(id = "a2", pages = listOf("x", "y"), srsState = SrsState(state = 2, repetitions = 3)),
            )
        )
        repos.deckRepository.addDeckWithCards(
            "B", listOf("en", "pl"), listOf("F", "B"),
            listOf(Flashcard(id = "b1", pages = listOf("x", "y"), srsState = SrsState(state = 1)))
        )
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val s = vm.stats.first { it.totalCards > 0 }
        assertEquals(3, s.totalCards)
        assertEquals(1, s.newCount) // a1
        assertEquals(1, s.learningCount) // b1 (state=1)
        assertEquals(1, s.masteredCount) // a2 (state=2, reps=3)
        assertEquals(2, s.decks.size)
    }

    @Test
    fun `DeckSummary per talia z osobnymi statystykami`() = runTest(dispatcher) {
        repos.deckRepository.addDeckWithCards(
            "DeckA", listOf("en", "pl"), listOf("F", "B"),
            listOf(Flashcard(id = "a1", pages = listOf("x", "y"), srsState = SrsState(state = 0)))
        )
        repos.deckRepository.addDeckWithCards(
            "DeckB", listOf("en", "pl"), listOf("F", "B"),
            listOf(
                Flashcard(id = "b1", pages = listOf("x", "y"), srsState = SrsState(state = 2, repetitions = 3)),
                Flashcard(id = "b2", pages = listOf("x", "y"), srsState = SrsState(state = 2, repetitions = 2)),
            )
        )
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        val decks = vm.stats.first { it.decks.size == 2 }.decks
        val deckA = decks.find { it.name == "DeckA" }
        val deckB = decks.find { it.name == "DeckB" }
        assertEquals(1, deckA?.stats?.total)
        assertEquals(1, deckA?.stats?.newCount)
        assertEquals(2, deckB?.stats?.total)
        assertEquals(1, deckB?.stats?.mastered)
        assertEquals(1, deckB?.stats?.review)
    }

    @Test
    fun `heatmapa pole istnieje w stanie`() = runTest(dispatcher) {
        repos.deckRepository.addDeckWithCards(
            "A", listOf("en", "pl"), listOf("F", "B"),
            listOf(Flashcard(id = "a1", pages = listOf("x", "y"), srsState = SrsState(state = 0)))
        )
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        // świeża talia bez ocen → heatmapa pusta (wcześniej asercja `is Map<*,*>` była zawsze prawdą)
        assertTrue(vm.stats.first().heatmap.isEmpty())
    }

    @Test
    fun `streak zero przy pustej heatmapie`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle(); com.raycoll.app.ui.VmTestHarness.flushMainLooper()
        assertEquals(0, vm.stats.first().streak)
    }
}
