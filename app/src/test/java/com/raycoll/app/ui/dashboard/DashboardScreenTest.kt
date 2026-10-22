package com.raycoll.app.ui.dashboard

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.ui.theme.RayCollTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val preferences = PreferencesRule()

    @Test
    fun `repeated mode selections keep neighbouring decks still in every drawn frame`() {
        val context = compose.activity
        val clock = FakeClock()
        val repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        runBlocking {
            repos.seed("en")
            preferences.repository.deckSwipeLearned()
        }
        val vm = DashboardViewModel(
            repos.deckRepository,
            repos.studyModeRepository,
            clock,
            preferences.repository,
            Dispatchers.Unconfined
        )
        try {
            compose.setContent {
                RayCollTheme(useSystemColors = false) {
                    DashboardScreen(
                        onStudy = {}, onBrowse = {}, onDeckSettings = {}, onImport = {}, onStats = {},
                        onArchive = {}, onAppSettings = {}, onManageModes = {}, viewModel = vm
                    )
                }
            }
            compose.waitForIdle()
            val state = vm.uiState.value as DashboardUiState.Ready
            val deck = state.decks.first()
            val list = findList(context.window.decorView)!!
            val neighbour = list.findViewHolderForAdapterPosition(1)!!.itemView
            val initialTop = neighbour.y
            val positions = mutableListOf<Float>()
            val observer = ViewTreeObserver.OnPreDrawListener {
                list.findViewHolderForAdapterPosition(1)?.itemView?.let { positions.add(it.y) }
                true
            }
            list.viewTreeObserver.addOnPreDrawListener(observer)
            val menuLabel = context.getString(
                R.string.a11y_item_action,
                context.getString(R.string.study_modes_title),
                deck.name
            )
            try {
                repeat(6) { iteration ->
                    val mode = state.modes[(iteration + 1) % state.modes.size]
                    val label = context.getString(modeNameRes(mode.id))
                    compose.onNodeWithContentDescription(menuLabel).performClick()
                    compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
                    compose.waitForIdle()
                    assertEquals(mode.id, (vm.uiState.value as DashboardUiState.Ready).decks.first().activeMode.id)
                }
                assertTrue("The test must observe drawn frames", positions.isNotEmpty())
                assertTrue("Neighbour moved from $initialTop through $positions", positions.all { it == initialTop })
            } finally {
                list.viewTreeObserver.removeOnPreDrawListener(observer)
            }
        } finally {
            vm.viewModelScope.cancel()
            compose.waitForIdle()
        }
    }

    private fun modeNameRes(modeId: String) = when (BuiltInMode.fromId(modeId)) {
        BuiltInMode.CLASSIC -> R.string.mode_classic_name
        BuiltInMode.LISTEN_SPEAK -> R.string.mode_listen_speak_name
        BuiltInMode.LISTEN_ONLY -> R.string.mode_listen_only_name
        BuiltInMode.BROWSE -> R.string.mode_browse_name
        BuiltInMode.AUTO_REVEAL -> R.string.mode_auto_reveal_name
        null -> error("Not a built-in mode: $modeId")
    }

    private fun findList(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findList(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
