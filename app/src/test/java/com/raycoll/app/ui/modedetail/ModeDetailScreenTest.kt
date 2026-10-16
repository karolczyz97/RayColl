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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
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

/** Edytor trybu: komunikaty o błędach, wskazówki i wyjście z niezapisanymi zmianami. */
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
        compose.onNodeWithText("Empty mode").performClick()
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
