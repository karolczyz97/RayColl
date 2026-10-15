package com.raycoll.app.ui.modedetail

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Edytor trybu: komunikaty o błędach, wskazówki i wyjście z niezapisanymi zmianami. */
@RunWith(RobolectricTestRunner::class)
class ModeDetailScreenTest {
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
        vm = ModeDetailViewModel(routeArgs("modeId" to null), repos.studyModeRepository)
        compose.setContent { ModeDetailScreen(onBack = { backCount++ }, viewModel = vm) }
        compose.onNodeWithText("Empty mode").performClick()
    }

    @After
    fun teardown() {
        vm.viewModelScope.cancel()
        compose.waitForIdle()
        repos.db.close()
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
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(1, backCount)
    }

    @Test
    fun `leaving with unsaved changes asks first and discarding goes back`() {
        nameField().performTextInput("Mine")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, backCount)

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, backCount)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard").performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun `the add step button is the last element of the list`() {
        compose.onNodeWithText("Add step").assertExists()
        compose.onNodeWithContentDescription("Add step").assertDoesNotExist()
    }
}
