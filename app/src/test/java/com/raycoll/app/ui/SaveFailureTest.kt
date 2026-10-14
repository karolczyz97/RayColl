package com.raycoll.app.ui

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
import com.raycoll.app.ui.archive.ArchiveViewModel
import com.raycoll.app.ui.browse.BrowseEvent
import com.raycoll.app.ui.browse.BrowseViewModel
import com.raycoll.app.ui.components.launchSaving
import com.raycoll.app.ui.dashboard.DashboardViewModel
import com.raycoll.app.ui.decksettings.DeckSettingsEvent
import com.raycoll.app.ui.decksettings.DeckSettingsViewModel
import com.raycoll.app.ui.imports.ImportEvent
import com.raycoll.app.ui.imports.ImportViewModel
import com.raycoll.app.ui.modedetail.ModeDetailEvent
import com.raycoll.app.ui.modedetail.ModeDetailViewModel
import com.raycoll.app.ui.studymodes.StudyModesViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Nieudany zapis z ekranu kończy się komunikatem, a nie zamknięciem aplikacji. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SaveFailureTest {

    @Test
    fun `browse mode editor and import report failed writes`() = runTest {
        val browse = BrowseViewModel(routeArgs("deckId" to "d1"), repos.deckRepository)
        val mode = ModeDetailViewModel(routeArgs("modeId" to null), repos.studyModeRepository)
        mode.chooseTemplate(ModeTemplate.CLASSIC)
        mode.setName("Custom")
        val imports = ImportViewModel(
            repos.deckRepository,
            DocumentFiles(ApplicationProvider.getApplicationContext()),
            UnconfinedTestDispatcher(testScheduler)
        )
        imports.setDeckName("Import")
        imports.onSourceTextChanged("q;a")
        breakDatabase()
        browse.addCard(listOf("q", "a"))
        assertEquals(BrowseEvent.SaveFailed, browse.events.first())
        mode.save()
        assertEquals(ModeDetailEvent.SaveFailed, mode.events.first())
        imports.runImport(listOf("A", "B"))
        assertEquals(ImportEvent.SaveFailed, imports.events.first())
        assertEquals(false, imports.uiState.value.importing)
    }
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
    }

    @After
    fun teardown() {
        repos.db.close()
        Dispatchers.resetMain()
    }

    /** Uszkodzona baza — każdy zapis kończy się wyjątkiem SQLite. */
    private fun breakDatabase() {
        val sql = repos.db.openHelper.writableDatabase
        listOf("cards", "decks", "study_modes").forEach { sql.execSQL("DROP TABLE $it") }
    }

    @Test
    fun `launchSaving reports failures but not cancellation`() = runTest(UnconfinedTestDispatcher()) {
        var failures = 0
        launchSaving(onFailure = { failures++ }) { throw IOException("disk full") }
        val job = launchSaving(onFailure = { failures++ }) { awaitCancellation() }
        job.cancel()
        assertEquals(1, failures)
    }

    @Test
    fun `deck settings report a failed write`() = runTest {
        val vm = DeckSettingsViewModel(routeArgs("deckId" to "d1"), repos.deckRepository, repos.studyModeRepository)
        breakDatabase()
        vm.rename("New")
        assertEquals(DeckSettingsEvent.SaveFailed, vm.events.first())
    }

    @Test
    fun `dashboard, archive and study modes report failed writes`() = runTest {
        val dashboard = DashboardViewModel(repos.deckRepository, repos.studyModeRepository, FakeClock())
        val archive = ArchiveViewModel(repos.deckRepository, FakeClock())
        val modes = StudyModesViewModel(repos.studyModeRepository)
        breakDatabase()

        dashboard.archiveDeck("d1")
        dashboard.events.first()
        archive.restore("d1")
        archive.events.first()
        modes.deleteMode("m1")
        modes.events.first()
    }
}
