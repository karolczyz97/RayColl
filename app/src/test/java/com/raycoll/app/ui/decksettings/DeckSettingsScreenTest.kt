package com.raycoll.app.ui.decksettings

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DeckSettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var repos: TestRepos
    private lateinit var vm: DeckSettingsViewModel

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
        val deckId = runBlocking {
            repos.deckRepository.addDeckWithCards(
                "Deck",
                listOf("en-US", "pl-PL"),
                listOf("A", "B"),
                listOf(listOf("x", "y"))
            )
        }
        vm = DeckSettingsViewModel(deckId, repos.deckRepository, repos.studyModeRepository)
    }

    @After
    fun teardown() {
        vm.viewModelScope.cancel()
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun `all study scope options stay reachable on a narrow screen`() {
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Study scope").fetchSemanticsNodes().isNotEmpty() }

        listOf("New + due for review", "New", "Review", "All").forEach { label ->
            val bounds = compose.onAllNodesWithText(label)[0].getUnclippedBoundsInRoot()
            assertTrue("$label sticks out: $bounds", bounds.right <= 320.dp)
        }
        compose.onNodeWithText("Cards to study").assertExists()
    }
}
