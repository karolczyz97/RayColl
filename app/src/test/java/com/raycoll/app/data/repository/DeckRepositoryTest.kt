package com.raycoll.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.ui.FakeClock
import com.raycoll.app.ui.TestRepos
import com.raycoll.app.ui.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DeckRepositoryTest {

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

    private suspend fun seedDeckWithCard(): String =
        repos.deckRepository.addDeckWithCards(
            "Deck", listOf("en-US", "pl-PL"), listOf("Front", "Back"),
            listOf(Flashcard(id = "c1", pages = listOf("hi", "cześć"), srsState = SrsState())),
        )

    @Test
    fun `reviewCard zapisuje heatmapę od pustej (pierwsza ocena dnia nie ginie)`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        val today = LocalDates.dayKey(clock.now())

        // Regresja: incrementActivity to UPDATE — bez utworzenia wiersza pierwsza ocena dnia
        // gubiłaby się i heatmapa/streak zostałyby puste na urządzeniu.
        assertNull(repos.deckRepository.getSnapshot().activityHeatmap[today])
        repos.deckRepository.reviewCard(deckId, "c1", 3)
        assertEquals(1, repos.deckRepository.getSnapshot().activityHeatmap[today])
        repos.deckRepository.reviewCard(deckId, "c1", 3)
        assertEquals(2, repos.deckRepository.getSnapshot().activityHeatmap[today])
    }

    @Test
    fun `reviewCard aktualizuje SRS karty i stempluje srsUpdatedAt`() = runTest(dispatcher) {
        val deckId = seedDeckWithCard()
        repos.deckRepository.reviewCard(deckId, "c1", 3)
        val card = repos.deckRepository.getDeck(deckId)!!.cards.first { it.id == "c1" }
        assertEquals(1, card.srsState.repetitions)
        assertEquals(clock.now(), card.srsUpdatedAt)
    }
}
