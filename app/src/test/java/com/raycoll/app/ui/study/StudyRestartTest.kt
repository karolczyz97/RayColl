package com.raycoll.app.ui.study

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.repository.PendingReview
import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.session.SpeakResult
import com.raycoll.app.playback.SessionControls
import com.raycoll.app.playback.StudySessionHolder
import com.raycoll.app.playback.StudySessionService
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** „Powtórz błędne” / „Zacznij od nowa” ponownie oddają sesję do pracy w tle (serwis + powiadomienie). */
@RunWith(RobolectricTestRunner::class)
class StudyRestartTest {

    @Test
    fun `failed reviews block restart and retry refreshes the finished summary`() {
        val vm = studyVm(listOf(ShowPageStep(), ShowRatingsStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        repos.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_review BEFORE INSERT ON cards BEGIN SELECT RAISE(ABORT, 'test review failure'); END"
        )
        vm.rate(3)
        idle()
        vm.rate(3)
        waitFor { (vm.uiState.value as? StudyUiState.Active)?.resultCards?.size == 2 }
        assertTrue((vm.uiState.value as StudyUiState.Active).resultCards!!.all { it.srsState.state == 0 })
        vm.restartSession()
        idle()
        assertTrue((vm.uiState.value as StudyUiState.Active).finished)
        repos.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_review")
        vm.retryReviewSave()
        waitFor { (vm.uiState.value as StudyUiState.Active).resultCards!!.all { it.srsState.repetitions == 1 } }
    }

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private lateinit var repos: TestRepos
    private lateinit var holder: StudySessionHolder

    @Before
    fun setup() {
        repos = VmTestHarness.create(app, clock)
        holder = StudySessionHolder(app)
    }

    @After
    fun teardown() {
        repos.db.close()
    }

    private fun idle(ms: Long = 0) {
        val looper = shadowOf(Looper.getMainLooper())
        if (ms > 0) looper.idleFor(Duration.ofMillis(ms)) else looper.idle()
    }

    /** Czeka na pracę na innych wątkach (DataStore, Room), pompując główny wątek. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "warunek nie spełniony w 5 s" }
            idle()
            Thread.sleep(10)
        }
    }

    private fun startedServices(): List<Intent> = generateSequence { shadowOf(app).nextStartedService }.toList()

    private fun studyVm(steps: List<ModeStep>): StudyViewModel {
        val prefs = UserPreferencesRepository(app)
        val deckId = runBlocking {
            // ustawienie urządzenia współdzielone między testami — jawnie: nauka w tle włączona
            prefs.setBackgroundPlayback(true)
            val id = repos.deckRepository.addDeckWithCards(
                "Deck",
                listOf("en-US", "pl-PL"),
                listOf("A", "B"),
                listOf(listOf("a1", "b1"), listOf("a2", "b2"))
            )
            val modeId = repos.studyModeRepository.addMode("Mode", steps)
            repos.deckRepository.setActiveStudyMode(id, modeId)
            id
        }
        val writes =
            ReviewWriteQueue(
                repos.deckRepository,
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
                repos.namespaceManager
            )
        return StudyViewModel(
            routeArgs("deckId" to deckId),
            repos.deckRepository,
            repos.studyModeRepository,
            prefs,
            holder,
            FakeSessionDeps(reviewWrites = writes),
            writes,
            clock
        )
    }

    private fun StudyViewModel.session() = (uiState.value as StudyUiState.Active).session

    @Test
    fun `summary reads cards after both reviews have been saved`() {
        val vm = studyVm(listOf(ShowPageStep(), ShowRatingsStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        vm.rate(3)
        idle()
        vm.rate(3)
        waitFor { (vm.uiState.value as? StudyUiState.Active)?.resultCards?.size == 2 }
        val state = vm.uiState.value as StudyUiState.Active
        assertTrue(state.finished)
        assertEquals(2, state.reviewedCardCount)
        assertTrue(state.resultCards!!.all { it.srsState.state == 2 && it.srsState.repetitions == 1 })
        assertTrue(state.session.cards.all { it.srsState.state == 0 })
        assertEquals(2, state.completedCardCount)
    }

    @Test
    fun `ending early counts only cards finished before the current one`() {
        val vm = studyVm(listOf(ShowPageStep(), ShowRatingsStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        vm.rate(3)
        waitFor { vm.session().currentCardIndex == 1 }
        vm.endSessionEarly()
        waitFor { (vm.uiState.value as StudyUiState.Active).finished }
        val state = vm.uiState.value as StudyUiState.Active
        assertTrue(state.endedEarly)
        assertEquals(1, state.completedCardCount)
        assertEquals(1, state.reviewedCardCount)
    }

    @Test
    fun `listening on a hidden page does not request microphone or prevent background playback`() {
        val vm = studyVm(listOf(ListenAndCheckStep(pageIndex = 2), WaitStep(ms = 60_000), NextCardStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        val state = vm.uiState.value as StudyUiState.Active
        assertFalse(state.needsMicrophone)
        assertTrue(state.runsInBackground)
        vm.endSessionEarly()
    }

    @Test
    fun `paused session is visible and resume follows service controls including focus refusal`() {
        val vm = studyVm(listOf(ShowPageStep(), WaitStep(ms = 60_000), NextCardStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        val engine = holder.current()!!
        engine.pause()
        idle()
        assertTrue((vm.uiState.value as StudyUiState.Active).paused)
        vm.tapCard()
        assertTrue(engine.isPaused.value)
        vm.skipToNextCard()
        idle()
        assertEquals(1, vm.session().currentCardIndex)
        assertTrue((vm.uiState.value as StudyUiState.Active).paused)
        var allowFocus = false
        var playCalls = 0
        val controls = object : SessionControls {
            override fun play(): Boolean {
                playCalls++
                if (allowFocus) engine.resume()
                return allowFocus
            }
            override fun pause() {}
            override fun next() {}
            override fun previous() {}
            override fun stop() {}
        }
        holder.attachControls(controls)
        vm.resume()
        assertTrue(engine.isPaused.value)
        allowFocus = true
        vm.resume()
        idle()
        assertFalse((vm.uiState.value as StudyUiState.Active).paused)
        assertEquals(2, playCalls)
        holder.detachControls(controls)
        vm.endSessionEarly()
    }

    @Test
    fun `restart after finishing starts the background service again for the same engine`() {
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 100), NextCardStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        val engine = holder.current()
        val first = startedServices()
        assertEquals(listOf(StudySessionService::class.java.name), first.map { it.component?.className })

        idle(1_000)
        assertEquals(SessionStatus.FINISHED, vm.session().status)
        assertTrue("zakończona sesja nie uruchamia serwisu", startedServices().isEmpty())

        vm.restartSession()
        waitFor { vm.session().status != SessionStatus.FINISHED }
        waitFor { shadowOf(app).peekNextStartedService() != null }
        val restarted = startedServices()
        assertEquals(listOf(StudySessionService::class.java.name), restarted.map { it.component?.className })
        // ten sam silnik (bez drugiej instancji) i przebieg już trwa, gdy serwis go przejmuje
        assertSame(engine, holder.current())
        assertNotEquals(SessionStatus.FINISHED, holder.current()!!.state.value.status)
    }

    @Test
    fun `mode that cannot run hands-free never starts the background service, also after restart`() {
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        assertTrue(startedServices().isEmpty())
        vm.endSessionEarly()
        idle()
        vm.restartSession()
        waitFor { vm.session().status != SessionStatus.FINISHED }
        idle()
        assertNull(shadowOf(app).peekNextStartedService())
    }
}
