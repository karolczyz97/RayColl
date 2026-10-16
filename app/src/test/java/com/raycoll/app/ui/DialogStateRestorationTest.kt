package com.raycoll.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.ui.browse.BrowseScreen
import com.raycoll.app.ui.browse.BrowseViewModel
import com.raycoll.app.ui.decksettings.DeckSettingsScreen
import com.raycoll.app.ui.decksettings.DeckSettingsViewModel
import com.raycoll.app.ui.modedetail.ModeDetailScreen
import com.raycoll.app.ui.modedetail.ModeDetailViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Otwarte okna i niezapisany tekst przeżywają odtworzenie stanu (obrót ekranu). */
@RunWith(RobolectricTestRunner::class)
class DialogStateRestorationTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @Test
    fun `failed card save keeps the draft and allows a successful retry`() {
        val vm = BrowseViewModel(deckId, repos.deckRepository, testPreferences.repository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        waitForText("cat")
        compose.onNodeWithContentDescription("Add card").performClick()
        field("Front").performTextInput("new question")
        field("Back").performTextInput("new answer")
        repos.store.failWhen = { it is com.raycoll.app.data.firestore.WriteOp.Set && "/cards/" in it.path }
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithText("Save").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled).not()
        }
        assertEquals("new question", fieldText("Front"))
        assertEquals("new answer", fieldText("Back"))
        compose.onNodeWithText("Save").assertIsEnabled()
        repos.store.failWhen = { false }
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { cards().size == 3 }
        assertEquals(listOf("new question", "new answer"), cards().last().pages)
        vm.viewModelScope.cancel()
        compose.waitForIdle()
    }

    @Test
    fun `mode reset confirmation stays open after state restoration`() {
        runBlocking { repos.seed("en") }
        val vm =
            ModeDetailViewModel("classic", repos.studyModeRepository, testPreferences.repository, SavedStateHandle())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ModeDetailScreen(onBack = {}, viewModel = vm) }
        waitForText("Classic")
        // niezmieniony wbudowany tryb nie ma czego przywracać
        compose.onNodeWithContentDescription("Restore defaults").assertIsNotEnabled()
        vm.setName("Classic edited")
        compose.onNodeWithContentDescription("Restore defaults").assertIsEnabled().performClick()
        compose.onNodeWithText("Restore defaults?").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Restore defaults?").assertExists()
        vm.viewModelScope.cancel()
        compose.waitForIdle()
    }

    @Test
    fun `discard changes confirmation stays open after state restoration`() {
        val vm = ModeDetailViewModel(null, repos.studyModeRepository, testPreferences.repository, SavedStateHandle())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ModeDetailScreen(onBack = {}, viewModel = vm) }
        compose.onNodeWithText("Empty mode").performClick()
        field("Mode name").performTextInput("Mine")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard changes?").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Discard changes?").assertExists()
        vm.viewModelScope.cancel()
        compose.waitForIdle()
    }

    @get:Rule
    val compose = createComposeRule()

    private val clock = FakeClock()
    private lateinit var repos: TestRepos
    private lateinit var deckId: String

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        deckId = runBlocking {
            repos.deckRepository.addDeckWithCards(
                "TestDeck",
                listOf("en-US", "pl-PL"),
                listOf("Front", "Back"),
                listOf(listOf("cat", "kot"), listOf("dog", "pies"))
            )
        }
    }

    @After
    fun teardown() {
    }

    private fun waitForText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun fieldText(label: String) =
        field(label).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun cards() = runBlocking { repos.deckRepository.getDeck(deckId)!!.cards }

    @Test
    fun `new card dialog keeps its draft after recreation, cancel drops it`() {
        val vm = BrowseViewModel(deckId, repos.deckRepository, testPreferences.repository)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        waitForText("cat")

        compose.onNodeWithContentDescription("Add card").performClick()
        field("Front").performTextInput("a very long question")
        field("Back").performTextInput("odpowiedź")

        restoration.emulateSavedInstanceStateRestore()

        assertEquals("a very long question", fieldText("Front"))
        assertEquals("odpowiedź", fieldText("Back"))
        // kursor zostaje tam, gdzie był (na końcu wpisanego tekstu), a nie wraca na początek
        val selection = field("Front").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange("a very long question".length), selection)
        field("Front").performTextInput("?")
        assertEquals("a very long question?", fieldText("Front"))

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Add card").performClick()
        assertEquals("anulowanie odrzuca szkic", "", fieldText("Front"))
    }

    @Test
    fun `edit dialog survives recreation and saves exactly once`() {
        val vm = BrowseViewModel(deckId, repos.deckRepository, testPreferences.repository)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        waitForText("cat")

        compose.onNodeWithText("cat").performClick()
        field("Front").performTextReplacement("kitten")

        restoration.emulateSavedInstanceStateRestore()

        assertEquals("kitten", fieldText("Front"))
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { cards().any { it.pages == listOf("kitten", "kot") } }
        assertEquals(2, cards().size)

        // kolejna karta nie dostaje tekstu poprzedniej
        compose.onNodeWithText("dog").performScrollTo().performClick()
        assertEquals("dog", fieldText("Front"))
    }

    @Test
    fun `delete deck dialog stays open with typed confirmation`() {
        val vm = DeckSettingsViewModel(deckId, repos.deckRepository, repos.studyModeRepository)
        val restoration = StateRestorationTester(compose)
        var closed = false
        restoration.setContent { DeckSettingsScreen(onBack = { closed = true }, onEditMode = {}, viewModel = vm) }
        waitForText("TestDeck")

        compose.onAllNodesWithText("Delete").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Type DELETE to confirm").assertExists()
        field("DELETE").performTextInput("del")

        restoration.emulateSavedInstanceStateRestore()

        assertEquals("del", fieldText("DELETE"))
        field("DELETE").performTextInput("ete")
        assertEquals("dopisanie po obrocie trafia na koniec", "delete", fieldText("DELETE"))
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.waitUntil(5_000) { closed }
        assertNotNull(runBlocking { repos.snapshot() }.groups.single().deletedAt)
    }
}
