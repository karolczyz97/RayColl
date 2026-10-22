package com.raycoll.app.ui.browse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Puste stany przeglądania: pusta talia (z przyciskiem dodania) różni się od braku wyników filtra. */
@RunWith(RobolectricTestRunner::class)
class BrowseScreenTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @Test
    fun `search action keeps the query and the clear button clears it`() {
        val vm = BrowseViewModel(deck(listOf(listOf("cat", "kot"))), repos.deckRepository, testPreferences.repository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
        searchField().performTextInput("cat")
        searchField().performImeAction()
        compose.waitUntil(5_000) { vm.search.value == "cat" }
        compose.onAllNodesWithContentDescription("Clear search").onLast().performClick()
        compose.waitUntil(5_000) { vm.search.value == "" }
        org.junit.Assert.assertEquals(
            "",
            searchField().fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
    }

    @After
    fun teardown() {
    }

    // po dotknięciu pasek otwiera wyszukiwanie na cały ekran — pole w nim jest ostatnim polem tekstowym
    private fun searchField() = compose.onAllNodes(hasSetTextAction()).onLast()

    private fun deck(cards: List<List<String>>) = runBlocking {
        repos.deckRepository.addDeckWithCards("Deck", listOf("en-US", "pl-PL"), listOf("Front", "Back"), cards)
    }

    @Test
    fun `empty deck offers adding the first card`() {
        val vm = BrowseViewModel(deck(emptyList()), repos.deckRepository, testPreferences.repository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("This deck has no flashcards yet.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add card").performClick()
        compose.onNode(hasSetTextAction() and hasText("Front")).assertIsDisplayed()
    }

    @Test
    fun `no search results keep the search field and say so`() {
        val vm = BrowseViewModel(deck(listOf(listOf("cat", "kot"))), repos.deckRepository, testPreferences.repository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
        searchField().performTextInput("zzz")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("No flashcards found.").fetchSemanticsNodes().isNotEmpty()
        }
        searchField().assertIsDisplayed()
    }

    @Test
    fun `swiping a card left deletes it without asking and undo brings it back`() {
        val deckId = deck(listOf(listOf("cat", "kot"), listOf("dog", "pies")))
        val vm = BrowseViewModel(deckId, repos.deckRepository, testPreferences.repository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        // pierwsza karta: na małym ekranie testowym dolna leży pod przyciskiem „+”
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("cat").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        // użytkownik zna gest — podpowiedź nie wraca
        org.junit.Assert.assertEquals(0, runBlocking { testPreferences.repository.preferences.first().swipeHintsLeft })
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `card editor offers save and cancel without a delete action`() {
        val deckId = deck(listOf(listOf("cat", "kot")))
        val vm = BrowseViewModel(deckId, repos.deckRepository, testPreferences.repository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("cat").performClick()
        compose.onNodeWithText("Delete flashcard").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        org.junit.Assert.assertEquals(1, runBlocking { repos.deckRepository.getDeck(deckId) }!!.cards.size)
    }
}
