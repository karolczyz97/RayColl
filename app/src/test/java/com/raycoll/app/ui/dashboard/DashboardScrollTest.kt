package com.raycoll.app.ui.dashboard

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.core.view.ViewCompat
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.customActionLabels
import com.raycoll.app.testing.hasCustomAction
import com.raycoll.app.testing.performCustomAction
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardScrollTest {
    @get:org.junit.Rule val testPreferences = com.raycoll.app.testing.PreferencesRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a deck handle offers move actions so a screen reader can reorder decks`() {
        val clock = FakeClock()
        val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        runBlocking {
            testPreferences.repository.deckSwipeLearned()
            listOf("First", "Second", "Third").forEach { name ->
                repos.deckRepository.addDeckWithCards(
                    name,
                    listOf("en", "pl"),
                    listOf("A", "B"),
                    listOf(listOf("q", "a"))
                )
            }
        }
        val vm = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock, testPreferences.repository)
        try {
            compose.setContent { DashboardScreen({}, {}, {}, {}, {}, {}, {}, {}, vm) }
            compose.waitUntil(5_000) { (vm.uiState.value as? DashboardUiState.Ready)?.decks?.size == 3 }
            compose.waitForIdle()
            // karta z oboma akcjami to ta w środku, skrajne mają po jednej
            val middle = compose.onNode(
                hasCustomAction("Move deck up") and hasCustomAction("Move deck down"),
                useUnmergedTree = true
            )
            assertEquals(
                listOf("Move deck up", "Move deck down"),
                middle.customActionLabels().filter { it.startsWith("Move deck") }
            )

            compose.performCustomAction(middle, "Move deck up")
            compose.waitUntil(5_000) {
                (vm.uiState.value as DashboardUiState.Ready).decks.map { it.name } == listOf("Second", "First", "Third")
            }
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }

    @Test
    fun `native fling does not start a competing toolbar animation`() {
        val clock = FakeClock()
        val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        runBlocking {
            testPreferences.repository.deckSwipeLearned()
            repeat(10) { index ->
                repos.deckRepository.addDeckWithCards(
                    "Deck $index",
                    listOf("en", "pl"),
                    listOf("A", "B"),
                    listOf(listOf("q", "a"))
                )
            }
        }
        val vm = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock, testPreferences.repository)
        try {
            compose.setContent { DashboardScreen({}, {}, {}, {}, {}, {}, {}, {}, vm) }
            compose.waitUntil(5_000) { (vm.uiState.value as? DashboardUiState.Ready)?.decks?.size == 10 }
            compose.waitForIdle()
            val list = compose.activity.findViewById<RecyclerView>(R.id.dashboard_deck_list)
            compose.runOnIdle {
                list.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                list.dispatchNestedPreScroll(0, 30, IntArray(2), null, ViewCompat.TYPE_TOUCH)
            }
            compose.waitForIdle()
            val heightWhileDragging = list.height

            // RecyclerView zgłasza początek własnego flingu, a nie jego zakończenie.
            compose.runOnIdle { list.dispatchNestedFling(0f, 2_500f, true) }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()

            assertEquals(
                "The toolbar must follow native scroll deltas instead of snapping during a fling",
                heightWhileDragging,
                list.height
            )
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }

    @Test
    fun `study and browse controls identify their deck and invoke the right action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repos = VmTestHarness.create(context, FakeClock())
        val id = runBlocking {
            repos.deckRepository.addDeckWithCards(
                "Biology",
                listOf("en-US", "pl-PL"),
                listOf("A", "B"),
                listOf(listOf("q", "a"))
            )
        }
        val vm =
            DashboardViewModel(repos.deckRepository, repos.studyModeRepository, FakeClock(), testPreferences.repository)
        var studied: String? = null
        var browsed: String? = null
        try {
            compose.setContent { DashboardScreen({ studied = it }, { browsed = it }, {}, {}, {}, {}, {}, {}, vm) }
            compose.waitUntil(5_000) { vm.uiState.value is DashboardUiState.Ready }
            val study = context.getString(R.string.a11y_item_action, context.getString(R.string.route_study), "Biology")
            compose.onNodeWithContentDescription(study).performClick()
            // dotknięcie nazwy talii otwiera podgląd; środek całej karty zajmuje uchwyt przeciągania
            compose.onNodeWithText("Biology", useUnmergedTree = true).performClick()
            assertEquals(id, studied)
            assertEquals(id, browsed)
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }

    @Test
    fun `updating a deck keeps the scrolled list visible`() {
        val clock = FakeClock()
        val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        val ids = runBlocking {
            (0..9).map { index ->
                repos.deckRepository.addDeckWithCards(
                    "Deck $index",
                    listOf("en-US", "pl-PL"),
                    listOf("A", "B"),
                    listOf(listOf("q", "a"))
                )
            }
        }
        val vm = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock, testPreferences.repository)
        try {
            compose.setContent { DashboardScreen({}, {}, {}, {}, {}, {}, {}, {}, vm) }
            compose.waitUntil(5_000) { (vm.uiState.value as? DashboardUiState.Ready)?.decks?.size == 10 }
            compose.runOnIdle {
                compose.activity.findViewById<RecyclerView>(R.id.dashboard_deck_list).scrollToPosition(9)
            }
            compose.onNodeWithText("Deck 9").assertIsDisplayed()
            runBlocking { repos.deckRepository.renameDeck(ids.last(), "Updated deck") }
            compose.waitUntil(5_000) {
                (vm.uiState.value as? DashboardUiState.Ready)?.decks?.any { it.name == "Updated deck" } == true
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Updated deck").assertIsDisplayed()
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }

    @Test
    fun `failed archive returns the deck to view`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClock()
        val repos = VmTestHarness.create(context, clock)
        val id = runBlocking {
            testPreferences.repository.deckSwipeLearned()
            repos.deckRepository.addDeckWithCards(
                "Biology",
                listOf("en", "pl"),
                listOf("A", "B"),
                listOf(listOf("q", "a"))
            )
        }
        val vm = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock, testPreferences.repository)
        try {
            compose.setContent { DashboardScreen({}, {}, {}, {}, {}, {}, {}, {}, vm) }
            compose.waitUntil(5_000) { vm.uiState.value is DashboardUiState.Ready }
            compose.onNodeWithText("Biology").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            repos.store.failWhen = { true }
            compose.onNodeWithText(context.getString(R.string.btn_archive)).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Biology").assertIsDisplayed()
            assertEquals(null, runBlocking { repos.deckRepository.getDeck(id) }!!.archivedAt)
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }
}
