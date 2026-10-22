package com.raycoll.app.ui.decksettings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.customActionLabels
import com.raycoll.app.testing.hasCustomAction
import com.raycoll.app.testing.longPressAndDrag
import com.raycoll.app.testing.performCustomAction
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
import org.robolectric.shadows.ShadowDialog

// Przeciągnięcie palcem o 12 × 20 px przechodzi przez sąsiedni wiersz niezależnie od jego wysokości.
private const val DRAG_STEPS = 12
private const val DRAG_STEP_PX = 20f

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
    fun `failed rename preserves the draft and can be retried`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val label = context.getString(R.string.settings_rename_label)
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo().performClick()
        repos.store.failWhen = { true }
        val field = compose.onNode(hasSetTextAction() and hasText(label))
        field.performClick().performTextReplacement("Failed edit")
        compose.onNodeWithText(context.getString(R.string.btn_save)).performClick()
        val error = context.getString(R.string.errors_save_failed)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "Failed edit",
            field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertEquals("Deck", runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name)
        repos.store.failWhen = { false }
        compose.onNodeWithText(context.getString(R.string.btn_save)).assertIsEnabled().performClick()
        waitForName("Failed edit")
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `editing a reordered page uses its current name and saves only when confirmed`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val label = context.getString(R.string.page_default_name, 1)
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        // czytnik ekranu przestawia strony akcją uchwytu zamiast przeciągania
        compose.performCustomAction(
            compose.onNode(hasCustomAction(context.getString(R.string.a11y_move_page_down)), useUnmergedTree = true),
            context.getString(R.string.a11y_move_page_down)
        )
        compose.waitUntil(5_000) {
            runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames == listOf("B", "A")
        }
        // środek całego wiersza trafia w osobny wybór języka; nazwa jest po lewej
        compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo().performClick()
        val field = compose.onNode(hasSetTextAction() and hasText(label))
        assertEquals("B", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        field.performClick().performTextReplacement("B edited")
        assertEquals(listOf("B", "A"), runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames)
        compose.onNodeWithText(context.getString(R.string.btn_save)).performClick()
        compose.waitUntil(5_000) {
            runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames == listOf("B edited", "A")
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp")
    fun `holding a page row and dragging it moves it only once, after it is dropped`() {
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Page 1").fetchSemanticsNodes().isNotEmpty() }

        val firstRow = compose.onNode(hasCustomAction("Move page down"), useUnmergedTree = true)
        compose.longPressAndDrag(firstRow, steps = DRAG_STEPS, stepPx = DRAG_STEP_PX) {
            // w trakcie przeciągania dane zostają, zmienia się tylko pokazana kolejność
            assertEquals(listOf("A", "B"), runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames)
        }

        compose.waitUntil(5_000) {
            runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames == listOf("B", "A")
        }
        assertEquals(listOf("y", "x"), runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.cards.single().pages)
    }

    @Test
    fun `a page row offers up and down actions only where the page can move`() {
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Page 1").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(
            listOf("Move page down"),
            compose.onNode(hasCustomAction("Move page down"), useUnmergedTree = true).customActionLabels()
        )
        assertEquals(
            listOf("Move page up"),
            compose.onNode(hasCustomAction("Move page up"), useUnmergedTree = true).customActionLabels()
        )
    }

    @Test
    fun `a cancelled rename never changes the saved deck`() {
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Deck name").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Deck name").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Only a draft")
        assertEquals("Deck", runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name)
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals("Deck", runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name)
        compose.onNodeWithText("Deck name").performClick()
        assertEquals(
            "Deck",
            compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
    }

    @Test
    fun `rename draft survives screen restoration and blank or unchanged names cannot be saved`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Deck name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Deck name").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Restored draft")
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(
            "Restored draft",
            compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertEquals("Deck", runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name)
        compose.onNodeWithText("Save").performClick()
        waitForName("Restored draft")
    }

    private fun waitForName(name: String) {
        compose.waitUntil(5_000) { runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name == name }
    }

    @Test
    fun `back closes the name editor without saving its draft`() {
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Deck name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Deck name").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Discarded on back")
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals("Deck", runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.name)
    }

    @Test
    fun `clearing a page name keeps the default page label`() {
        compose.setContent { DeckSettingsScreen(onBack = {}, onEditMode = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Page 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Page 1", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repos.deckRepository.getDeck(vm.deckId) }!!.pageNames.first().isEmpty()
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Page 1").assertExists()
    }
}
