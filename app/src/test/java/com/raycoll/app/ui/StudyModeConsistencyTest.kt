package com.raycoll.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.DEFAULT_MODE_ID
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.operations.isModifiedBuiltIn
import com.raycoll.app.domain.seed.BuiltInMode
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
import com.raycoll.app.ui.components.displayName
import com.raycoll.app.ui.dashboard.DashboardUiState
import com.raycoll.app.ui.dashboard.DashboardViewModel
import com.raycoll.app.ui.decksettings.DeckSettingsUiState
import com.raycoll.app.ui.decksettings.DeckSettingsViewModel
import com.raycoll.app.ui.decksettings.isDeleteConfirmed
import com.raycoll.app.ui.modedetail.ModeDetailScreen
import com.raycoll.app.ui.modedetail.ModeDetailUiState
import com.raycoll.app.ui.modedetail.ModeDetailViewModel
import kotlinx.coroutines.flow.filterIsInstance
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
import org.robolectric.annotation.Config

/** Tryb talii, nazwy trybów wbudowanych i potwierdzenie usunięcia — spójnie na wszystkich ekranach. */
@RunWith(RobolectricTestRunner::class)
class StudyModeConsistencyTest {

    @get:Rule
    val compose = createComposeRule()

    private val clock = FakeClock()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), clock)
        runBlocking { repos.localStore.seedIfEmpty("en") }
    }

    @After
    fun teardown() {
        repos.db.close()
    }

    @Test
    fun `deleted custom mode falls back to the same classic mode on dashboard, settings and in the session`() =
        runBlocking {
            val customId = repos.studyModeRepository.addMode("Mine", listOf(ShowPageStep()))
            val deckId = repos.deckRepository.addDeckWithCards(
                "Deck",
                listOf("en-US", "pl-PL"),
                listOf("A", "B"),
                listOf(listOf("a", "b"))
            )
            repos.deckRepository.setActiveStudyMode(deckId, customId)
            repos.studyModeRepository.deleteMode(customId)

            val dashboard = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, clock)
            val summary = dashboard.uiState.filterIsInstance<DashboardUiState.Ready>()
                .first { state -> state.decks.any { it.id == deckId } }
                .decks.single { it.id == deckId }
            val settings = DeckSettingsViewModel(
                routeArgs("deckId" to deckId),
                repos.deckRepository,
                repos.studyModeRepository
            ).uiState.filterIsInstance<DeckSettingsUiState.Ready>().first()
            val storedModeId = repos.deckRepository.getDeck(deckId)!!.activeModeId
            val session = repos.studyModeRepository.getModeOrDefault(storedModeId)

            assertEquals(DEFAULT_MODE_ID, summary.activeMode.id)
            assertEquals(DEFAULT_MODE_ID, settings.activeMode.id)
            assertEquals(DEFAULT_MODE_ID, session.id)
            assertEquals("ten sam tryb, nie fabryczna kopia", session, summary.activeMode)
            // zapisana referencja talii nie jest po cichu przepisywana
            assertEquals(customId, repos.deckRepository.getDeck(deckId)!!.activeModeId)
        }

    @Test
    fun `fallback uses the user's edited classic mode, the one the screens show`() = runBlocking {
        val classic = repos.studyModeRepository.getMode(DEFAULT_MODE_ID)!!
        repos.studyModeRepository.updateMode(classic.copy(steps = listOf(ShowPageStep())))
        val resolved = repos.studyModeRepository.getModeOrDefault("deleted-or-missing")
        assertEquals(listOf(ShowPageStep()), resolved.steps)
    }

    @Test
    fun `typing and erasing a letter in a built-in name keeps the canonical name and saves nothing`() {
        val vm = ModeDetailViewModel(routeArgs("modeId" to DEFAULT_MODE_ID), repos.studyModeRepository)
        compose.setContent { ModeDetailScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { vm.uiState.value is ModeDetailUiState.Editing }

        val nameField = compose.onNode(hasSetTextAction() and hasText("Classic"))
        nameField.performTextReplacement("Classicx")
        assertEquals("Classicx", (vm.uiState.value as ModeDetailUiState.Editing).name)
        compose.onNode(hasSetTextAction() and hasText("Classicx")).performTextReplacement("Classic")
        assertEquals(BuiltInMode.CLASSIC.defaultName, (vm.uiState.value as ModeDetailUiState.Editing).name)
        val shown = compose.onNode(hasSetTextAction() and hasText("Classic"))
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("Classic", shown)

        vm.save()
        compose.waitForIdle()
        val stored = runBlocking { repos.studyModeRepository.getMode(DEFAULT_MODE_ID)!! }
        assertEquals(BuiltInMode.CLASSIC.defaultName, stored.name)
        assertEquals("nic się nie zmieniło — bez zapisu", 0L, stored.updatedAt)
        assertFalse(stored.isModifiedBuiltIn())
    }

    @Test
    fun `a real custom name of a built-in mode is kept and marks the mode as modified`() {
        val vm = ModeDetailViewModel(routeArgs("modeId" to DEFAULT_MODE_ID), repos.studyModeRepository)
        compose.setContent { ModeDetailScreen(onBack = {}, viewModel = vm) }
        compose.waitUntil(5_000) { vm.uiState.value is ModeDetailUiState.Editing }

        compose.onNode(hasSetTextAction() and hasText("Classic")).performTextReplacement("Moje fiszki")
        vm.save()
        compose.waitUntil(5_000) {
            runBlocking { repos.studyModeRepository.getMode(DEFAULT_MODE_ID)!!.updatedAt != 0L }
        }
        val stored = runBlocking { repos.studyModeRepository.getMode(DEFAULT_MODE_ID)!! }
        assertEquals("Moje fiszki", stored.name)
        assertTrue(stored.isModifiedBuiltIn())
    }

    @Test
    @Config(qualifiers = "de")
    fun `built-in name follows the app language until the user renames it`() {
        val classic = runBlocking { repos.studyModeRepository.getMode(DEFAULT_MODE_ID)!! }
        val renamed = classic.copy(name = "Moje fiszki")
        val shown = mutableListOf<String>()
        compose.setContent {
            shown += classic.displayName()
            shown += renamed.displayName()
        }
        compose.waitForIdle()
        assertEquals(listOf("Klassisch", "Moje fiszki"), shown.take(2))
    }

    @Test
    fun `delete confirmation ignores letter case but requires the whole word`() {
        assertTrue(isDeleteConfirmed("usuń", "USUŃ"))
        assertTrue(isDeleteConfirmed("Usuń", "USUŃ"))
        assertTrue(isDeleteConfirmed(" USUŃ ", "USUŃ"))
        assertFalse(isDeleteConfirmed("usu", "USUŃ"))
        assertFalse(isDeleteConfirmed("usun", "USUŃ"))
        assertFalse(isDeleteConfirmed("", "USUŃ"))
    }
}
