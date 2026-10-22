package com.raycoll.app.ui

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.ui.browse.CardEditorDialog
import com.raycoll.app.ui.components.SwipeAction
import com.raycoll.app.ui.components.SwipeReveal
import com.raycoll.app.ui.components.SwipeToDeleteItem
import com.raycoll.app.ui.decksettings.DeckSettingsScreen
import com.raycoll.app.ui.decksettings.DeckSettingsViewModel
import java.time.Duration
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UiInteractionRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `cancelled dashboard hint returns the card to its original position`() = checkHintCancellation { hint ->
        SwipeReveal(
            actions = listOf(SwipeAction("Action", ColorPainter(Color.Black), Color.White, Color.Black, onClick = {})),
            showHint = hint
        ) { Box(Modifier.size(100.dp, 80.dp).testTag("card")) }
    }

    @Test
    fun `cancelled flashcard hint returns the card to its original position`() = checkHintCancellation { hint ->
        SwipeToDeleteItem(onDelete = {}, deleteLabel = "Delete", shape = RectangleShape, showHint = hint) {
            Box(Modifier.size(100.dp, 80.dp).testTag("card"))
        }
    }

    private fun checkHintCancellation(content: @Composable (Boolean) -> Unit) {
        var hint by mutableStateOf(false)
        compose.setContent {
            MaterialTheme { Box(Modifier.padding(start = 120.dp, top = 32.dp)) { content(hint) } }
        }
        val start = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { hint = true }
        compose.mainClock.advanceTimeByFrame()
        var shifted = start
        repeat(100) {
            if (shifted >= start - 1f) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                compose.mainClock.advanceTimeBy(16)
                shifted = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
            }
        }
        assertTrue("The hint must actually be in progress: start=$start shifted=$shifted", shifted < start - 1f)
        compose.runOnIdle { hint = false }
        compose.mainClock.advanceTimeBy(2_000)
        val end = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.left
        assertEquals("Cancelling a hint must not leave the card shifted", start, end, 0.5f)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `card editor can save again after restoring without an active save operation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CardEditorDialog("Edit", "card", listOf("A", "B"), listOf("Front", "Back"), {}, {})
        }
        compose.onNodeWithText("Save").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `card editor uses default labels for cleared page names`() {
        compose.setContent {
            CardEditorDialog("Edit", "card", listOf("A", "B"), listOf("", ""), {}, {})
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText(context.getString(R.string.page_default_name, 1)).assertExists()
    }

    @Test
    fun `failed mode deletion in deck settings shows error before undo`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repos = VmTestHarness.create(context, FakeClock())
        val deckId = runBlocking {
            repos.studyModeRepository.addMode("Custom mode", emptyList())
            repos.deckRepository.addDeckWithCards("Deck", listOf("en", "pl"), listOf("A", "B"), emptyList())
        }
        val vm = DeckSettingsViewModel(deckId, repos.deckRepository, repos.studyModeRepository)
        try {
            compose.setContent { DeckSettingsScreen({}, {}, vm) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Custom mode").fetchSemanticsNodes().isNotEmpty()
            }
            repos.store.failWhen = { true }
            val delete = context.getString(
                R.string.a11y_item_action,
                context.getString(R.string.btn_delete),
                "Custom mode"
            )
            compose.onNodeWithContentDescription(delete).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(R.string.btn_undo)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.errors_save_failed)).assertExists()
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }
}
