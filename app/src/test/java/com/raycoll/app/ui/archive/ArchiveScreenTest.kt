package com.raycoll.app.ui.archive

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.ui.theme.RayCollTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ArchiveScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repos: TestRepos
    private lateinit var viewModel: ArchiveViewModel
    private lateinit var archivedId: String
    private lateinit var activeId: String
    private var openedDeckId: String? = null
    private val deckName = "Archived deck"

    @Before
    fun setup() {
        val clock = FakeClock()
        repos = VmTestHarness.create(context, clock)
        runBlocking {
            activeId = addDeck("Active deck")
            archivedId = addDeck(deckName)
            repos.deckRepository.archiveDeck(archivedId)
        }
        viewModel = ArchiveViewModel(repos.deckRepository, clock, Dispatchers.Unconfined)
    }

    @After
    fun teardown() {
        viewModel.viewModelScope.cancel()
        compose.waitForIdle()
    }

    private suspend fun addDeck(name: String) = repos.deckRepository.addDeckWithCards(
        name,
        listOf("en", "pl"),
        listOf("Front", "Back"),
        listOf(listOf("Question", "Answer"))
    )

    private fun showArchive(fontScale: Float = 1f, expectedName: String = deckName) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RayCollTheme {
                    ArchiveScreen(onBack = {}, onBrowse = { openedDeckId = it }, viewModel = viewModel)
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(expectedName).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun deleteButton() = compose.onNodeWithText(context.getString(R.string.archive_delete_permanently))

    private fun revealActions(name: String = deckName) {
        compose.onNodeWithText(name).performScrollTo().performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    private fun waitForEmptyArchive() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.archive_empty)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `one delete click immediately empties archive and deleted deck cannot be restored`() {
        showArchive()
        deleteButton().assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.btn_restore)).assertDoesNotExist()

        revealActions()
        deleteButton().performClick()

        waitForEmptyArchive()
        assertNull(openedDeckId)
        runBlocking {
            assertNull(repos.deckRepository.getDeck(archivedId))
            repos.deckRepository.restoreDeck(archivedId)
            assertNull(repos.deckRepository.getDeck(archivedId))
            assertNotNull(repos.deckRepository.getDeck(activeId))
        }
    }

    @Test
    fun `failed delete keeps deck visible and retry succeeds`() {
        showArchive()
        repos.store.failWhen = { true }

        revealActions()
        deleteButton().performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.errors_save_failed))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(deckName).assertIsDisplayed()
        assertNotNull(runBlocking { repos.deckRepository.getDeck(archivedId) })

        repos.store.failWhen = { false }
        revealActions()
        deleteButton().performClick()

        waitForEmptyArchive()
        assertNull(runBlocking { repos.deckRepository.getDeck(archivedId) })
    }

    @Test
    fun `restore still returns the deck with its cards to active decks`() {
        showArchive()

        revealActions()
        compose.onNodeWithText(context.getString(R.string.btn_restore)).performClick()

        waitForEmptyArchive()
        assertNull(openedDeckId)
        val restored = runBlocking { repos.deckRepository.getDeck(archivedId) }!!
        assertNull(restored.archivedAt)
        assertEquals(listOf("Question", "Answer"), restored.cards.single().pages)
    }

    @Test
    fun `clicking an archived deck opens its cards without restoring it`() {
        showArchive()

        compose.onNodeWithText(deckName).performClick()

        assertEquals(archivedId, openedDeckId)
        val deck = runBlocking { repos.deckRepository.getDeck(archivedId) }!!
        assertNotNull(deck.archivedAt)
        assertEquals(1, deck.cards.size)
    }

    @Test
    fun `clicking a revealed card closes its actions before opening the original browse screen`() {
        showArchive()
        revealActions()

        compose.onNodeWithText(deckName).performClick()

        compose.waitForIdle()
        deleteButton().assertDoesNotExist()
        assertNull(openedDeckId)

        compose.onNodeWithText(deckName).performClick()
        assertEquals(archivedId, openedDeckId)
    }

    @Test
    fun `revealing another archived deck hides the previous actions and deletes only the selected deck`() {
        val otherId = runBlocking {
            addDeck("Other archived deck").also { repos.deckRepository.archiveDeck(it) }
        }
        showArchive()
        revealActions()
        revealActions("Other archived deck")

        assertEquals(1, compose.onAllNodesWithText(context.getString(R.string.btn_restore)).fetchSemanticsNodes().size)
        deleteButton().performClick()

        compose.waitUntil(5_000) {
            (viewModel.uiState.value as? ArchiveUiState.Ready)?.decks?.map { it.id } == listOf(archivedId)
        }
        runBlocking {
            assertNull(repos.deckRepository.getDeck(otherId))
            assertNotNull(repos.deckRepository.getDeck(archivedId))
            assertNotNull(repos.deckRepository.getDeck(activeId))
        }
    }

    @Test
    fun `archived deck shows the shared stats bar without study controls`() {
        showArchive()

        val statsDescription = listOf(
            R.string.srs_badge_new to 1,
            R.string.srs_badge_learning to 0,
            R.string.srs_badge_review to 0,
            R.string.srs_badge_mastered to 0
        ).joinToString(", ") { (label, count) ->
            context.getString(R.string.filter_chip_count, context.getString(label), count)
        }
        compose.onNodeWithContentDescription(statsDescription).assertIsDisplayed()
        listOf(R.string.route_study, R.string.study_modes_title).forEach { action ->
            val label = context.getString(R.string.a11y_item_action, context.getString(action), deckName)
            compose.onNodeWithContentDescription(label).assertDoesNotExist()
        }
        compose.onNodeWithText(context.getString(R.string.dashboard_due_count, 1)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "pl-w320dp-h800dp")
    fun `long names and large Polish text keep both actions reachable on a narrow screen`() {
        val longName = "Bardzo długa nazwa zarchiwizowanej talii do nauki języka angielskiego"
        runBlocking { repos.deckRepository.renameDeck(archivedId, longName) }
        showArchive(fontScale = 1.6f, expectedName = longName)

        revealActions(longName)
        val restore = compose.onNodeWithText(context.getString(R.string.btn_restore)).assertIsDisplayed()
        val delete = deleteButton().assertIsDisplayed()
        val restoreBounds = restore.getUnclippedBoundsInRoot()
        val deleteBounds = delete.getUnclippedBoundsInRoot()
        listOf(restoreBounds, deleteBounds).forEach { bounds ->
            assertTrue("Action is outside the screen: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
        }
        assertTrue(
            "Actions overlap: $restoreBounds and $deleteBounds",
            restoreBounds.right <= deleteBounds.left || deleteBounds.right <= restoreBounds.left ||
                restoreBounds.bottom <= deleteBounds.top || deleteBounds.bottom <= restoreBounds.top
        )

        delete.performClick()
        waitForEmptyArchive()
    }
}
