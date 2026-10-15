package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.IdTokenResult
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.data.sync.BootstrapLoader
import com.raycoll.app.data.sync.CloudLoad
import com.raycoll.app.data.sync.CloudSync
import com.raycoll.app.data.sync.CurrentUser
import com.raycoll.app.data.sync.MigrationController
import com.raycoll.app.data.sync.MigrationOption
import com.raycoll.app.data.sync.SIGN_OUT_PUSH_TIMEOUT_MS
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.data.sync.SyncStatus
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Logowanie: wybór konta żyje z ekranem (obrót go przerywa), reszta w ViewModelu. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppSettingsSignInTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @get:Rule val folder = TemporaryFolder()

    private class FakeGoogle : GoogleSignIn {
        val tokenRequest = CompletableDeferred<IdTokenResult>()
        val signedInTokens = mutableListOf<String>()
        var signedIn = false
        var signOutCalls = 0
        var signInGate: CompletableDeferred<Unit>? = null
        override val account: StateFlow<Account?> = MutableStateFlow(null)
        override suspend fun requestGoogleIdToken(activityContext: Context) = tokenRequest.await()
        override suspend fun signInWithIdToken(idToken: String): SignInResult {
            signedInTokens += idToken
            signInGate?.await()
            signedIn = true
            return SignInResult.Success(Account("uid-1", null))
        }
        override suspend fun signOut() {
            signOutCalls++
            signedIn = false
        }
    }

    private class EmptyCloud : CloudSync {
        var loads = 0
        var saves = 0
        var hangLoads = false
        override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.IDLE)
        override suspend fun load(uid: String): CloudLoad {
            loads++
            if (hangLoads) awaitCancellation() // brak sieci: Firestore czeka na serwer
            return CloudLoad.Failed
        }
        override suspend fun hasFreshBaseline(uid: String) = false
        override suspend fun save(uid: String, data: StoreData): Boolean {
            saves++
            return false
        }
        override fun clearBaseline() {}
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val google = FakeGoogle()
    private val cloud = EmptyCloud()
    private lateinit var repos: TestRepos
    private lateinit var vm: AppSettingsViewModel
    private lateinit var sync: SyncLifecycle
    private lateinit var appScope: CoroutineScope

    private val main = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(main)
        repos = VmTestHarness.create(context, FakeClock())
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        sync = newSync(appScope)
        vm = AppSettingsViewModel(
            testPreferences.repository,
            BackupRepository(repos.localStore, DocumentFiles(context), Dispatchers.Unconfined),
            google,
            sync,
            FakeClock()
        )
    }

    private fun newSync(scope: CoroutineScope): SyncLifecycle {
        val user = object : CurrentUser {
            override fun currentUid() = if (google.signedIn) "uid-1" else null
        }
        return SyncLifecycle(
            user,
            repos.localStore,
            repos.deckRepository,
            repos.namespaceManager,
            BootstrapLoader(user, repos.localStore, repos.namespaceManager, cloud),
            MigrationController(repos.localStore, repos.namespaceManager),
            cloud,
            DataMutations(),
            FakeClock(),
            scope,
            google
        )
    }

    @After
    fun teardown() {
        vm.viewModelScope.cancel()
        appScope.cancel()
        repos.db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `backup commands cannot overlap and unlock after completion`() = runTest(main) {
        val file = File(folder.root, "should-not-exist.json")
        vm.factoryReset()
        vm.factoryReset()
        vm.exportBackup(Uri.fromFile(file))
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.FactoryResetDone, vm.events.first())
        assertEquals(FakeClock().now(), repos.localStore.snapshot().replacementAt)
        assertTrue(!file.exists())
        vm.exportBackup(Uri.fromFile(file))
        advanceUntilIdle()
        assertEquals(
            AppSettingsEvent.BackupExported(com.raycoll.app.data.repository.BackupResult.SUCCESS),
            vm.events.first()
        )
        assertTrue(file.exists())
    }

    @Test
    fun `backup failure unlocks the next operation`() = runTest(main) {
        repos.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_reset BEFORE INSERT ON decks BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        vm.factoryReset()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.SaveFailed, vm.events.first())
        repos.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reset")
        vm.factoryReset()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.FactoryResetDone, vm.events.first())
    }

    @Test
    fun `preferences are loading until the first stored value arrives`() {
        org.junit.Assert.assertNull(vm.uiState.value.preferences)
    }

    @Test
    fun `screen recreated during account choice cancels it without signing in`() = runTest(main) {
        val collector = launch { vm.uiState.collect {} }
        val screen = launch { vm.chooseGoogleAccount(context) }
        vm.uiState.first { it.signingIn }

        screen.cancel() // obrót: zakres korutyn ekranu znika razem z Activity
        vm.uiState.first { !it.signingIn }
        advanceUntilIdle()
        assertTrue(google.signedInTokens.isEmpty())
        assertEquals(0, cloud.loads)
        collector.cancel()
    }

    @Test
    fun `chosen account signs in with the token and starts syncing in the application`() = runTest(main) {
        val collector = launch { vm.uiState.collect {} }
        val screen = launch { vm.chooseGoogleAccount(context) }
        vm.uiState.first { it.signingIn }
        google.tokenRequest.complete(IdTokenResult.Token("token-1"))
        runCurrent() // ekran odebrał token
        // ekran może już zniknąć — logowanie tokenem i synchronizacja dzieją się w aplikacji
        screen.cancel()
        vm.uiState.first { !it.signingIn }
        advanceUntilIdle()
        assertEquals(listOf("token-1"), google.signedInTokens)
        assertEquals("bootstrap konta po zalogowaniu", 1, cloud.loads)
        collector.cancel()
    }

    @Test
    fun `closing view model during token sign in still switches to account namespace`() = runTest(main) {
        val gate = CompletableDeferred<Unit>()
        google.signInGate = gate
        google.tokenRequest.complete(IdTokenResult.Token("token-1"))
        vm.chooseGoogleAccount(context)
        runCurrent()
        vm.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("uid-1", repos.namespaceManager.current())
        assertEquals(1, cloud.loads)
    }

    @Test
    fun `signed in firebase never uploads or merges the guest namespace`() = runTest(main) {
        google.signedIn = true
        sync.pushNow()
        assertEquals(NamespaceManager.GUEST, repos.namespaceManager.current())
        assertEquals(0, cloud.loads)
        assertEquals(0, cloud.saves)
    }

    @Test
    fun `cancelling migration signs out once and returns to guest`() = runTest(main) {
        google.signedIn = true
        repos.namespaceManager.switchTo("uid-1")
        sync.resolveMigration(MigrationOption.CANCEL)
        assertEquals(1, google.signOutCalls)
        assertEquals(NamespaceManager.GUEST, repos.namespaceManager.current())
    }

    @Test
    fun `sign out offline waits for the upload only up to the limit`() = runTest(main) {
        val sync = newSync(backgroundScope)
        google.signedIn = true
        repos.namespaceManager.switchTo("uid-1")
        cloud.hangLoads = true
        sync.signOut().join()
        assertEquals(SIGN_OUT_PUSH_TIMEOUT_MS, testScheduler.currentTime)
        assertEquals(1, google.signOutCalls)
        assertEquals(NamespaceManager.GUEST, repos.namespaceManager.current())
    }

    @Test
    fun `sign out offline interrupts an upload that started earlier`() = runTest(main) {
        val sync = newSync(backgroundScope)
        google.signedIn = true
        repos.namespaceManager.switchTo("uid-1")
        cloud.hangLoads = true
        backgroundScope.launch { sync.pushNow() } // np. koniec sesji nauki chwilę wcześniej
        runCurrent()
        sync.signOut().join()
        assertEquals(SIGN_OUT_PUSH_TIMEOUT_MS, testScheduler.currentTime)
        assertEquals(1, google.signOutCalls)
        assertEquals(NamespaceManager.GUEST, repos.namespaceManager.current())
    }

    @Test
    fun `failed account choice is reported`() = runTest(main) {
        launch { vm.chooseGoogleAccount(context) }
        advanceUntilIdle()
        google.tokenRequest.complete(IdTokenResult.Failure(SignInError.PLAY_SERVICES))
        assertEquals(AppSettingsEvent.SignInFailed(SignInError.PLAY_SERVICES), vm.events.first())
        assertTrue(google.signedInTokens.isEmpty())
    }
}
