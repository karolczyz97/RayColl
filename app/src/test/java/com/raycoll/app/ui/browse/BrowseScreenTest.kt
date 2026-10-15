package com.raycoll.app.ui.browse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
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
    @Test
    fun `search action keeps the query and the clear button clears it`() {
        val vm = BrowseViewModel(routeArgs("deckId" to deck(listOf(listOf("cat", "kot")))), repos.deckRepository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("cat")
        field.performImeAction()
        org.junit.Assert.assertEquals("cat", vm.search.value)
        compose.onNodeWithContentDescription("Clear search").performClick()
        org.junit.Assert.assertEquals("", vm.search.value)
        org.junit.Assert.assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
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

    private fun deck(cards: List<List<String>>) = runBlocking {
        repos.deckRepository.addDeckWithCards("Deck", listOf("en-US", "pl-PL"), listOf("Front", "Back"), cards)
    }

    @Test
    fun `empty deck offers adding the first card`() {
        val vm = BrowseViewModel(routeArgs("deckId" to deck(emptyList())), repos.deckRepository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("This deck has no flashcards yet.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add card").performClick()
        compose.onNode(hasSetTextAction() and hasText("Front")).assertIsDisplayed()
    }

    @Test
    fun `no search results keep the search field and say so`() {
        val vm = BrowseViewModel(routeArgs("deckId" to deck(listOf(listOf("cat", "kot")))), repos.deckRepository)
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("No flashcards found.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }
}
