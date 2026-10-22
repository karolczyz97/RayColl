package com.raycoll.app.ui.studymodes

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// dwie sekcje i opis na górze listy wymagają wyższego okna, żeby własny tryb był na ekranie
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1000dp")
class StudyModesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repos: TestRepos
    private lateinit var viewModel: StudyModesViewModel
    private var editedId: String? = null

    @Before
    fun setup() {
        repos = VmTestHarness.create(context, FakeClock())
        runBlocking {
            repos.put(StoreData(studyModes = listOf(customMode, builtInMode)))
        }
        viewModel = StudyModesViewModel(repos.studyModeRepository)
    }

    @After
    fun teardown() {
        viewModel.viewModelScope.cancel()
        compose.waitForIdle()
    }

    private fun showModes() {
        compose.setContent {
            StudyModesScreen(onBack = {}, onEditMode = { editedId = it }, onNewMode = {}, viewModel = viewModel)
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(customMode.name).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `click opens the editor without separate edit and delete buttons`() {
        showModes()
        compose.onNodeWithContentDescription(actionLabel(R.string.mode_list_edit, customMode)).assertDoesNotExist()
        compose.onNodeWithContentDescription(actionLabel(R.string.btn_delete, customMode)).assertDoesNotExist()
        compose.onNodeWithContentDescription(actionLabel(R.string.mode_list_edit, builtInMode)).assertDoesNotExist()

        compose.onNodeWithText(customMode.name).performClick()

        assertEquals(customMode.id, editedId)
        assertNotNull(runBlocking { repos.studyModeRepository.getMode(customMode.id) })
    }

    @Test
    fun `swiping a custom mode deletes it and undo restores a clickable row`() {
        showModes()
        compose.onNodeWithText(customMode.name).performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(customMode.name).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(context.getString(R.string.btn_undo)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(customMode.name).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText(customMode.name).assertIsDisplayed().performClick()

        assertEquals(customMode.id, editedId)
        assertNotNull(runBlocking { repos.studyModeRepository.getMode(customMode.id) })
    }

    @Test
    fun `built in modes cannot be swiped away and hide reset after restoring defaults`() {
        showModes()
        compose.onNodeWithContentDescription(actionLabel(R.string.mode_list_reset, builtInMode)).assertExists()

        compose.onNodeWithText(builtInMode.name).performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText(builtInMode.name).assertIsDisplayed().performClick()
        assertEquals(builtInMode.id, editedId)
        assertNotNull(runBlocking { repos.studyModeRepository.getMode(builtInMode.id) })

        compose.onNodeWithContentDescription(actionLabel(R.string.mode_list_reset, builtInMode)).performClick()
        compose.onNodeWithText(context.getString(R.string.mode_list_reset)).performClick()
        val defaultName = context.getString(R.string.mode_classic_name)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(defaultName).fetchSemanticsNodes().isNotEmpty() }
        val resetLabel = context.getString(
            R.string.a11y_item_action,
            context.getString(R.string.mode_list_reset),
            defaultName
        )
        compose.onNodeWithContentDescription(resetLabel).assertDoesNotExist()
        assertEquals(SEED_MODES.first().name, runBlocking { repos.studyModeRepository.getMode(builtInMode.id) }!!.name)
    }

    @Test
    fun `failed swipe deletion shows an error and returns the mode to view`() {
        showModes()
        repos.store.failWhen = { true }

        compose.onNodeWithText(customMode.name).performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.errors_save_failed))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(5_000)

        compose.onNodeWithText(customMode.name).assertIsDisplayed().performClick()
        assertEquals(customMode.id, editedId)
        assertNotNull(runBlocking { repos.studyModeRepository.getMode(customMode.id) })
    }

    private fun actionLabel(action: Int, mode: StudyMode) =
        context.getString(R.string.a11y_item_action, context.getString(action), mode.name)

    private val customMode = StudyMode("custom", "Custom mode")
    private val builtInMode = SEED_MODES.first().copy(name = "Built-in test mode")
}
