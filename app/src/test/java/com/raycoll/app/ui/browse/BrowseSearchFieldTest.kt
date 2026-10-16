package com.raycoll.app.ui.browse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pole wyszukiwania nie czeka na filtr liczony w tle — szybkie pisanie nie gubi znaków (A4). */
@RunWith(RobolectricTestRunner::class)
class BrowseSearchFieldTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var repos: TestRepos
    private lateinit var deckId: String
    private val filterExecutor = Executors.newSingleThreadExecutor()

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
        deckId = runBlocking {
            repos.deckRepository.addDeckWithCards(
                "Deck",
                listOf("en-US", "pl-PL"),
                listOf("Front", "Back"),
                listOf(listOf("cat", "kot"), listOf("zebra", "zebra"))
            )
        }
    }

    @After
    fun teardown() {
        filterExecutor.shutdownNow()
    }

    @Test
    fun `characters typed while the filter is stalled all stay in the field`() {
        val vm = BrowseViewModel(
            deckId,
            repos.deckRepository,
            testPreferences.repository,
            filterExecutor.asCoroutineDispatcher()
        )
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }

        val filterStalled = CountDownLatch(1)
        filterExecutor.execute { filterStalled.await() } // filtr nie policzy niczego, dopóki piszemy
        // pole w otwartym wyszukiwaniu (pasek po dotknięciu rozwija się na cały ekran)
        val field = { compose.onAllNodes(hasSetTextAction()).onLast() }
        "zebra".forEach { field().performTextInput(it.toString()) }

        assertEquals("zebra", field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(
            "cat nadal widoczny — filtr stoi",
            compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty()
        )
        filterStalled.countDown()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isEmpty() }
    }
}
