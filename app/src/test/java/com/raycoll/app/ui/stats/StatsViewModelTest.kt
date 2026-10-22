package com.raycoll.app.ui.stats

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.MainDispatcherRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {

    @Test
    fun `open statistics refresh the date without a database mutation`() = runTest(dispatcher) {
        val vm = vm()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val today = vm.ready().today
        clock.advance(86_400_000L)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(today.plusDays(1), vm.ready { it.today == today.plusDays(1) }.today)
        collector.cancel()
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcher = MainDispatcherRule(dispatcher)
    private lateinit var repos: TestRepos

    private val clock = FakeClock()

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
    }

    private fun vm() = mainDispatcher.track(StatsViewModel(repos.deckRepository, clock))

    private suspend fun StatsViewModel.ready(predicate: (StatsUiState.Ready) -> Boolean = { true }) =
        uiState.filterIsInstance<StatsUiState.Ready>().first(predicate)

    private suspend fun seedDeck(
        name: String,
        languages: List<String>,
        pageNames: List<String>,
        cards: List<Flashcard>
    ) {
        val data = repos.snapshot()
        val deck =
            FlashcardGroup(id = name, name = name, cards = cards, pageLanguages = languages, pageNames = pageNames)
        repos.put(data.copy(groups = data.groups + deck))
    }

    @Test
    fun `pusty stan przy braku talii`() = runTest(dispatcher) {
        val vm = vm()
        val s = vm.ready()
        assertEquals(0, s.allCards.total)
        assertEquals(0, s.streak)
        assertTrue(s.decks.isEmpty())
    }

    @Test
    fun `stats liczy karty z wielu talii`() = runTest(dispatcher) {
        seedDeck(
            "A",
            listOf("en", "pl"),
            listOf("F", "B"),
            listOf(
                Flashcard(id = "a1", pages = listOf("x", "y"), srsState = SrsState(state = 0)),
                Flashcard(id = "a2", pages = listOf("x", "y"), srsState = SrsState(state = 2, repetitions = 3))
            )
        )
        seedDeck(
            "B",
            listOf("en", "pl"),
            listOf("F", "B"),
            listOf(Flashcard(id = "b1", pages = listOf("x", "y"), srsState = SrsState(state = 1)))
        )
        val vm = vm()
        val s = vm.ready { it.allCards.total > 0 }
        assertEquals(3, s.allCards.total)
        assertEquals(1, s.allCards.newCount) // a1
        assertEquals(1, s.allCards.learning) // b1 (state=1)
        assertEquals(1, s.allCards.mastered) // a2 (state=2, reps=3)
        assertEquals(2, s.decks.size)
    }

    @Test
    fun `DeckSummary per talia z osobnymi statystykami`() = runTest(dispatcher) {
        seedDeck(
            "DeckA",
            listOf("en", "pl"),
            listOf("F", "B"),
            listOf(Flashcard(id = "a1", pages = listOf("x", "y"), srsState = SrsState(state = 0)))
        )
        seedDeck(
            "DeckB",
            listOf("en", "pl"),
            listOf("F", "B"),
            listOf(
                Flashcard(id = "b1", pages = listOf("x", "y"), srsState = SrsState(state = 2, repetitions = 3)),
                Flashcard(id = "b2", pages = listOf("x", "y"), srsState = SrsState(state = 2, repetitions = 2))
            )
        )
        val vm = vm()
        val decks = vm.ready { it.decks.size == 2 }.decks
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
        seedDeck(
            "A",
            listOf("en", "pl"),
            listOf("F", "B"),
            listOf(Flashcard(id = "a1", pages = listOf("x", "y"), srsState = SrsState(state = 0)))
        )
        val vm = vm()
        // świeża talia bez ocen → heatmapa pusta (wcześniej asercja `is Map<*,*>` była zawsze prawdą)
        assertTrue(vm.ready().heatmap.isEmpty())
    }

    @Test
    fun `streak zero przy pustej heatmapie`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(0, vm.ready().streak)
    }

    @Test
    fun `streak liczony od wstrzykniętego zegara, nie czasu systemowego`() = runTest(dispatcher) {
        // regresja: VM używał System.currentTimeMillis() zamiast Clock
        val today = LocalDates.dayKey(clock.current)
        val yesterday = LocalDates.dayKey(clock.current - 86_400_000L)
        repos.setActivity(today, 2)
        repos.setActivity(yesterday, 1)
        val vm = vm()
        assertEquals(2, vm.ready { it.heatmap.isNotEmpty() }.streak)
    }
}
