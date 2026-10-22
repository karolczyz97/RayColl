package com.raycoll.app.ui.modedetail

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.customActionLabels
import com.raycoll.app.testing.hasCustomAction
import com.raycoll.app.testing.longPressAndDrag
import com.raycoll.app.testing.performCustomAction
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Przeciągnięcie palcem o 12 × 20 px przechodzi przez sąsiedni krok niezależnie od jego wysokości.
private const val DRAG_STEPS = 12
private const val DRAG_STEP_PX = 20f

/** Edytor trybu: komunikaty o błędach, wskazówki, przestawianie kroków i wyjście z niezapisanymi zmianami. */
@RunWith(RobolectricTestRunner::class)
class ModeDetailScreenTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @Test
    fun `exiting issue banner keeps its text and a new issue is shown immediately`() {
        nameField().performTextInput("Mine")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        vm.addStep(ShowPageStep())
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("• Add at least one step").assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("• Add at least one step").assertDoesNotExist()
        vm.removeStep(0)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("• Add at least one step").assertExists()
        compose.mainClock.autoAdvance = true
    }

    @get:Rule
    val compose = createComposeRule()

    private lateinit var repos: TestRepos
    private lateinit var vm: ModeDetailViewModel
    private var backCount = 0

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
        vm = ModeDetailViewModel(null, repos.studyModeRepository, testPreferences.repository, SavedStateHandle())
        compose.setContent { ModeDetailScreen(onBack = { backCount++ }, viewModel = vm) }
        // szablonów jest tyle, że na małym oknie testowym ostatni wymaga przewinięcia
        compose.onNodeWithText("Empty mode").performScrollTo().performClick()
    }

    @After
    fun teardown() {
        vm.viewModelScope.cancel()
        compose.waitForIdle()
    }

    private fun nameField() = compose.onNode(hasSetTextAction() and hasText("Mode name"))

    @Test
    fun `a new mode does not show a name error before the user typed anything`() {
        compose.onNodeWithText("• Add at least one step").assertExists()
        compose.onNodeWithText("• Mode name is required").assertDoesNotExist()
        compose.onNodeWithText("Mode name is required").assertDoesNotExist()

        nameField().performTextInput("a")
        nameField().performTextClearance()
        compose.onNodeWithText("• Mode name is required").assertExists()
        compose.onNodeWithText("Mode name is required").assertExists()
    }

    @Test
    fun `a missing terminal step is a tip, not an error, and saving stays possible`() {
        nameField().performTextInput("Mine")
        vm.addStep(ShowPageStep())
        compose.waitForIdle()

        compose.onNodeWithText("Tips:").assertExists()
        compose.onNodeWithText("Issues in mode configuration:").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp")
    fun `holding a step and dragging it moves it after the step it was dragged over`() {
        vm.addStep(ShowPageStep(pageIndex = 0))
        vm.addStep(ShowPageStep(pageIndex = 1))
        vm.addStep(ShowPageStep(pageIndex = 2))
        compose.waitForIdle()

        compose.longPressAndDrag(firstStepCard(), steps = DRAG_STEPS, stepPx = DRAG_STEP_PX) {
            // przeciągany krok jest już na nowym miejscu, zanim palec się oderwie
            assertTrue(pageOrder().first() != 0)
        }

        val order = pageOrder()
        assertTrue(order.indexOf(0) >= 1)
        assertEquals(listOf(1, 2), order.filter { it != 0 })
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp")
    fun `a step offers move actions only in the directions it can go`() {
        vm.addStep(ShowPageStep(pageIndex = 0))
        vm.addStep(ShowPageStep(pageIndex = 1))
        compose.waitForIdle()

        assertEquals(listOf("Delete: 1. Show page 1", "Move down"), firstStepCard().customActionLabels())
        assertEquals(
            listOf("Delete: 2. Show page 2", "Move up"),
            compose.onNode(hasCustomAction("Move up"), useUnmergedTree = true).customActionLabels()
        )

        compose.performCustomAction(firstStepCard(), "Move down")
        assertEquals(listOf(1, 0), pageOrder())
    }

    @Test
    @Config(qualifiers = "w400dp-h1200dp")
    fun `swiping a step to the left removes it and undo brings it back`() {
        vm.addStep(ShowPageStep(pageIndex = 0))
        vm.addStep(ShowPageStep(pageIndex = 1))
        compose.waitForIdle()

        firstStepCard().performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { pageOrder() == listOf(1) }

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { pageOrder() == listOf(0, 1) }
    }

    // pierwszy krok może się przesunąć tylko w dół (środkowe mają obie akcje)
    private fun firstStepCard() =
        compose.onNode(hasCustomAction("Move down") and !hasCustomAction("Move up"), useUnmergedTree = true)

    private fun pageOrder() = (vm.uiState.value as ModeDetailUiState.Editing).steps.map {
        (it.step as ShowPageStep).pageIndex
    }

    @Test
    fun `leaving an untouched editor does not ask anything`() {
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(1, backCount)
    }

    @Test
    fun `leaving with unsaved changes asks first and discarding goes back`() {
        nameField().performTextInput("Mine")

        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, backCount)

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, backCount)

        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard").performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun `the add step button is the last element of the list`() {
        compose.onNodeWithText("Add step").assertExists()
        compose.onNodeWithContentDescription("Add step").assertDoesNotExist()
    }

    @Test
    fun `add step shows nine blocks by default and also seventeen individual steps in expert mode`() {
        compose.onNodeWithText("Expert mode").assertIsOff()
        compose.onNodeWithText("Add step").performScrollTo().performClick()
        compose.onNodeWithText("Ready-made blocks").assertExists()
        // Dziewięć bloków oraz przycisk zamknięcia okna.
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog())).assertCountEquals(10)
        compose.onNodeWithText("Display").assertDoesNotExist()
        compose.onNodeWithText("Show page").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("Expert mode").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.expertMode.value
        }
        compose.onNodeWithText("Expert mode").assertIsOn()
        assertTrue(runBlocking { testPreferences.repository.preferences.first().expertMode })

        compose.onNodeWithText("Add step").performScrollTo().performClick()
        compose.onNodeWithText("Ready-made blocks").assertExists()
        compose.onNodeWithText("Display").assertExists()
        compose.onNodeWithText("Show page").assertExists()
        // Dziewięć bloków, siedemnaście kroków oraz przycisk zamknięcia okna.
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog())).assertCountEquals(27)
    }

    @Test
    fun `individual steps stay visible and editable after expert mode is switched off`() {
        compose.onNodeWithText("Expert mode").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.expertMode.value
        }
        compose.onNodeWithText("Add step").performScrollTo().performClick()
        compose.onNodeWithText("Show page").performScrollTo().performClick()

        compose.onNodeWithText("Expert mode").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            !vm.expertMode.value
        }
        compose.onNodeWithText("Expert mode").assertIsOff()
        compose.onNode(hasSetTextAction() and hasText("Page"))
            .performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("1. Show page 2").assertExists()
        val step = (vm.uiState.value as ModeDetailUiState.Editing).steps.single().step as ShowPageStep
        assertEquals(1, step.pageIndex)
        assertFalse(runBlocking { testPreferences.repository.preferences.first().expertMode })
    }
}
