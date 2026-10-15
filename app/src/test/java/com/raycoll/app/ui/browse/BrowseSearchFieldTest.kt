package com.raycoll.app.ui.browse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pole wyszukiwania nie czeka na filtr liczony w tle — szybkie pisanie nie gubi znaków (A4). */
@RunWith(RobolectricTestRunner::class)
class BrowseSearchFieldTest {

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
            routeArgs("deckId" to deckId),
            repos.deckRepository,
            filterExecutor.asCoroutineDispatcher()
        )
        compose.setContent { BrowseScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }

        val filterStalled = CountDownLatch(1)
        filterExecutor.execute { filterStalled.await() } // filtr nie policzy niczego, dopóki piszemy
        val field = compose.onNode(hasSetTextAction())
        "zebra".forEach { field.performTextInput(it.toString()) }

        assertEquals("zebra", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("cat nadal widoczny — filtr stoi", 1, compose.onAllNodesWithText("cat").fetchSemanticsNodes().size)
        filterStalled.countDown()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isEmpty() }
    }
}
