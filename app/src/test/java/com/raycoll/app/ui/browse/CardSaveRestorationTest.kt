package com.raycoll.app.ui.browse

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.firestore.Doc
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardSaveRestorationTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val preferences = PreferencesRule()

    @Test
    fun `new process preserves the draft and allows retrying an interrupted save`() = interruptedSave(newProcess = true)

    @Test
    fun `rotation keeps an active save blocked until the operation finishes`() = interruptedSave(newProcess = false)

    private fun interruptedSave(newProcess: Boolean) {
        val clock = FakeClock()
        val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        val deckId = runBlocking {
            preferences.repository.swipeLearned()
            repos.deckRepository.addDeckWithCards(
                "Deck",
                listOf("en", "pl"),
                listOf("A", "B"),
                listOf(listOf("q", "a"))
            )
        }
        val delayedStore = object : DocumentStore by repos.store {
            var gate: CompletableDeferred<Unit>? = null
            var waiting = false
            override suspend fun cachedDoc(path: String): Doc? {
                gate?.let {
                    waiting = true
                    it.await()
                }
                return repos.store.cachedDoc(path)
            }
        }
        val deckRepository = DeckRepository(delayedStore, repos.account, clock)
        val models = mutableListOf<BrowseViewModel>()
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent {
                val vm = remember {
                    if (!newProcess && models.isNotEmpty()) return@remember models.single()
                    BrowseViewModel(deckId, deckRepository, preferences.repository, Dispatchers.Unconfined)
                        .also(models::add)
                }
                BrowseScreen({}, vm)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("q").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("q").performClick()
            compose.onNode(hasSetTextAction() and hasText("q")).performTextReplacement("new question")
            delayedStore.gate = CompletableDeferred()
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) { delayedStore.waiting }
            compose.onNodeWithText("Save").assertIsNotEnabled()
            val pendingWrite = delayedStore.gate!!
            if (newProcess) {
                models.single().viewModelScope.cancel()
                delayedStore.gate = null
            }

            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithText("new question").assertExists()
            if (newProcess) {
                assertEquals(2, models.size)
                assertNotSame(models[0], models[1])
                compose.onNodeWithText("Save").assertIsEnabled().performClick()
            } else {
                assertEquals(1, models.size)
                compose.onNodeWithText("Save").assertIsNotEnabled()
                compose.onNodeWithText("Cancel").assertIsNotEnabled()
                compose.onNodeWithText("new question").assertIsNotEnabled()
                // A duplicate callback must also be ignored by the ViewModel.
                models.single().updateCard(
                    runBlocking { repos.deckRepository.getDeck(deckId) }!!.cards.single().id,
                    listOf("duplicate", "a")
                )
                pendingWrite.complete(Unit)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
            compose.waitUntil(5_000) {
                runBlocking { repos.deckRepository.getDeck(deckId) }!!.cards.single().pages ==
                    listOf("new question", "a")
            }
            assertTrue(models.last().savingCard.value.not())
        } finally {
            models.forEach { it.viewModelScope.cancel() }
            compose.waitForIdle()
        }
    }
}
