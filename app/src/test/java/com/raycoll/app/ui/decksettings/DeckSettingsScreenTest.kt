package com.raycoll.app.ui.decksettings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Cards to study").fetchSemanticsNodes().isNotEmpty() }

        listOf("New + due for review", "New", "Review", "All").forEach { label ->
            val bounds = compose.onAllNodesWithText(label)[0].getUnclippedBoundsInRoot()
            assertTrue("$label sticks out: $bounds", bounds.right <= 320.dp)
        }
        compose.onNodeWithText("Card order").assertExists()
    }

    @Test
    fun `failed rename shows the stored name again`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val label = context.getString(R.string.settings_rename_label)
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        repos.store.failWhen = { true }
        val field = compose.onNode(hasSetTextAction() and hasText(label))
        field.performClick().performTextReplacement("Failed edit")
        compose.waitUntil(5_000) {
            field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "Deck"
        }
        assertEquals("Deck", runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name)
    }

    @Test
    fun `moving a focused page refreshes its name before another edit`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val label = context.getString(R.string.import_page_label, 1)
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNode(hasSetTextAction() and hasText(label))
        field.performClick()
        val moveDown = context.getString(
            R.string.a11y_item_action,
            context.getString(R.string.a11y_move_page_down),
            "A"
        )
        compose.onNodeWithContentDescription(moveDown).performClick()
        compose.waitUntil(5_000) {
            field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "B"
        }
        field.performClick().performTextReplacement("B edited")
        compose.waitUntil(5_000) {
            runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames == listOf("B edited", "A")
        }
    }
}
