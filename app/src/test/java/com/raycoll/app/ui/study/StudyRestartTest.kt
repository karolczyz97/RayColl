package com.raycoll.app.ui.study

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.session.ListenStatus
import com.raycoll.app.domain.session.SessionError
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.playback.AudioFocusRequester
import com.raycoll.app.playback.FocusChange
import com.raycoll.app.playback.StudySessionHolder
import com.raycoll.app.playback.StudySessionService
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import java.time.Duration
import kotlinx.coroutines.flow.first
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** „Powtórz błędne” / „Zacznij od nowa” ponownie oddają sesję do pracy w tle (serwis + powiadomienie). */
@RunWith(RobolectricTestRunner::class)
class StudyRestartTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private lateinit var repos: TestRepos
    private lateinit var holder: StudySessionHolder
    private var serviceStartFails = false
    private var appVisible = true
    private var allowFocus = true
    private val focus = object : AudioFocusRequester {
        override fun request(onChange: (FocusChange) -> Unit): Boolean = allowFocus
        override fun abandon() {}
    }

    @Before
    fun setup() {
        repos = VmTestHarness.create(app, clock)
        holder = StudySessionHolder(
            app,
            { context, intent ->
                if (serviceStartFails) throw IllegalStateException("background start not allowed")
                ContextCompat.startForegroundService(context, intent)
            },
            { appVisible },
            focus
        )
    }

    @After
    fun teardown() {
        holder.current()?.let(holder::release)
    }

    private fun idle(ms: Long = 0) {
        val looper = shadowOf(Looper.getMainLooper())
        if (ms > 0) looper.idleFor(Duration.ofMillis(ms)) else looper.idle()
    }

    /** Czeka na pracę na innych wątkach (DataStore), pompując główny wątek. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "warunek nie spełniony w 5 s" }
            idle()
            Thread.sleep(10)
        }
    }

    private fun startedServices(): List<Intent> = generateSequence { shadowOf(app).nextStartedService }.toList()

    private fun studyVm(
        steps: List<ModeStep>,
        deps: FakeSessionDeps = FakeSessionDeps(deckRepository = repos.deckRepository)
    ): StudyViewModel {
        val prefs = testPreferences.repository
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
        return vm(deckId, deps)
    }

    private fun vm(deckId: String, deps: FakeSessionDeps = FakeSessionDeps(deckRepository = repos.deckRepository)) =
        StudyViewModel(
            deckId,
            repos.deckRepository,
            repos.studyModeRepository,
            testPreferences.repository,
            holder,
            deps,
            clock
        )

    private fun StudyViewModel.session() = (uiState.value as StudyUiState.Active).session

    @Test
    fun `night mode recreation keeps the session revealed answers and one saved review`() {
        val deps = FakeSessionDeps(deckRepository = repos.deckRepository)
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), ShowPageStep(pageIndex = 1), ShowRatingsStep()), deps)
        waitFor { vm.uiState.value is StudyUiState.Active && vm.session().status == SessionStatus.AWAITING_RATING }
        vm.rate(3)
        waitFor { vm.session().currentCardIndex == 1 && vm.session().status == SessionStatus.AWAITING_RATING }
        val before = vm.uiState.value
        val engine = holder.current()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(vm))
        }
        try {
            assertSame(vm, ViewModelProvider(activity.get(), factory)[StudyViewModel::class.java])
            val oldActivity = activity.get()
            val configuration = Configuration(oldActivity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            activity.configurationChange(configuration)
            idle()
            assertNotEquals(oldActivity, activity.get())
            assertSame(vm, ViewModelProvider(activity.get(), factory)[StudyViewModel::class.java])
            assertSame(engine, holder.current())
            assertEquals(before, vm.uiState.value)
            assertEquals(listOf(0, 1), vm.session().revealedPages)
            assertEquals(1, deps.reviews.size)
        } finally {
            activity.pause().stop().destroy()
        }
    }

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
        // karty sesji też mają już stan po ocenie (pasek postępu pokazuje aktualną kategorię)
        assertTrue(state.session.cards.all { it.srsState.state == 2 })
        assertEquals(2, state.completedCardCount)
        vm.endSessionEarly() // spóźnione potwierdzenie otwartego wcześniej dialogu
        idle()
        val after = vm.uiState.value as StudyUiState.Active
        assertFalse(after.endedEarly)
        assertEquals(2, after.completedCardCount)
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
        val vm = studyVm(listOf(ShowPageStep(), SpeakPageStep(), WaitStep(ms = 60_000), NextCardStep()))
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
        allowFocus = false
        vm.resume()
        idle()
        assertTrue(engine.isPaused.value)
        assertEquals(StudyEvent.AudioFocusDenied, runBlocking { vm.events.first() })
        allowFocus = true
        vm.resume()
        idle()
        assertFalse((vm.uiState.value as StudyUiState.Active).paused)
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
    fun `app returning to foreground does not resume a pause it did not cause`() {
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        val engine = holder.current()!!
        engine.pause()
        vm.onAppBackground()
        vm.onAppForeground()
        assertTrue(engine.isPaused.value)
        vm.endSessionEarly()
    }

    @Test
    fun `app in background pauses a running session without background mode and resumes it`() {
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), ShowRatingsStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        val engine = holder.current()!!
        vm.onAppBackground()
        assertTrue(engine.isPaused.value)
        vm.onAppForeground()
        assertFalse(engine.isPaused.value)
        vm.endSessionEarly()
    }

    @Test
    fun `refused service start does not crash and keeps the session in foreground mode`() {
        serviceStartFails = true
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000), NextCardStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        idle()
        assertFalse((vm.uiState.value as StudyUiState.Active).runsInBackground)
        assertTrue(startedServices().isEmpty())
        vm.onAppBackground()
        assertTrue(holder.current()!!.isPaused.value)
        vm.onAppForeground()
        assertFalse(holder.current()!!.isPaused.value)
        vm.endSessionEarly()
    }

    @Test
    fun `session registered while the app is already in background goes to background mode`() {
        appVisible = false
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000), NextCardStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        idle()
        val engine = holder.current()!!
        assertTrue(engine.inBackground.value)
        assertFalse(engine.isPaused.value)
        vm.onAppForeground()
        assertFalse(engine.inBackground.value)
        vm.endSessionEarly()
    }

    @Test
    fun `refused service start with the app in background pauses until it returns`() {
        serviceStartFails = true
        appVisible = false
        val vm = studyVm(listOf(ShowPageStep(pageIndex = 0), WaitStep(ms = 60_000), NextCardStep()))
        waitFor { vm.uiState.value is StudyUiState.Active }
        idle()
        val engine = holder.current()!!
        assertFalse(engine.inBackground.value)
        assertTrue(engine.isPaused.value)
        vm.onAppForeground()
        assertFalse(engine.isPaused.value)
        vm.endSessionEarly()
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

    @Test
    fun `unknown deck shows the missing deck state`() {
        val vm = vm("missing-deck")
        waitFor { vm.uiState.value != StudyUiState.Loading }
        assertEquals(StudyUiState.DeckMissing, vm.uiState.value)
        assertNull(holder.current())
    }

    @Test
    fun `deck without due cards shows the nothing due state`() {
        val deckId = runBlocking {
            val id = repos.deckRepository.addDeckWithCards(
                "Done",
                listOf("en-US", "pl-PL"),
                listOf("A", "B"),
                listOf(listOf("a1", "b1"))
            )
            val card = repos.deckRepository.getDeck(id)!!.cards.single()
            repos.deckRepository.reviewCard(id, card.id, 3)
            id
        }
        val vm = vm(deckId)
        waitFor { vm.uiState.value != StudyUiState.Loading }
        assertEquals(StudyUiState.NoDueCards("Done"), vm.uiState.value)
        assertNull(holder.current())
    }

    @Test
    fun `microphone permission after a successful listen does not restart the card`() {
        val deps = FakeSessionDeps(deckRepository = repos.deckRepository)
        val vm = studyVm(listOf(ListenAndCheckStep(pageIndex = 1), ShowRatingsStep()), deps)
        waitFor { vm.uiState.value is StudyUiState.Active && deps.listenTimeouts.size == 1 }
        vm.onMicrophoneGranted()
        idle()
        assertEquals(1, deps.listenTimeouts.size)
        vm.endSessionEarly()
    }

    @Test
    fun `microphone permission restarts the card once after a failed listen`() {
        val deps = FakeSessionDeps(deckRepository = repos.deckRepository, listenStatus = ListenStatus.ERROR)
        val vm = studyVm(listOf(ListenAndCheckStep(pageIndex = 1), ShowRatingsStep()), deps)
        waitFor { vm.uiState.value is StudyUiState.Active && deps.listenTimeouts.size == 1 }
        idle()
        assertEquals(StudyEvent.SpeechError(SessionError.STT), runBlocking { vm.events.first() })
        deps.listenStatus = ListenStatus.COMPLETED

        vm.onMicrophoneGranted()
        waitFor { deps.listenTimeouts.size == 2 }
        assertEquals(0, vm.session().currentCardIndex)

        vm.onMicrophoneGranted()
        idle()
        assertEquals("zgoda bez nowego błędu nie restartuje karty", 2, deps.listenTimeouts.size)
        vm.endSessionEarly()
    }
}
