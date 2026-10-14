package com.raycoll.app.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `updating a deck keeps the scrolled list visible`() {
        val clock = FakeClock()
        val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        val ids = runBlocking {
            (0..9).map { index ->
                repos.deckRepository.addDeckWithCards(
                    "Deck $index",
                    listOf("en-US", "pl-PL"),
                    listOf("A", "B"),
                    listOf(listOf("q", "a"))
                )
            }
        }
        val vm = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock)
        try {
            compose.setContent { DashboardScreen({}, {}, {}, {}, {}, {}, {}, {}, vm) }
            compose.waitUntil(5_000) { (vm.uiState.value as? DashboardUiState.Ready)?.decks?.size == 10 }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(9)
            compose.onNodeWithText("Deck 9").assertIsDisplayed()
            runBlocking { repos.deckRepository.renameDeck(ids.last(), "Updated deck") }
            compose.waitUntil(5_000) {
                (vm.uiState.value as? DashboardUiState.Ready)?.decks?.any { it.name == "Updated deck" } == true
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Updated deck").assertIsDisplayed()
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
            repos.db.close()
        }
    }
}
