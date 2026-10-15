package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.firestore.SyncStatus
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.IdTokenResult
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupRepository
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

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val google = FakeGoogle()
    private lateinit var repos: TestRepos
    private lateinit var vm: AppSettingsViewModel
    private lateinit var sync: com.raycoll.app.data.account.AccountManager
    private lateinit var appScope: CoroutineScope

    private val main = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(main)
        repos = VmTestHarness.create(context, FakeClock())
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        sync = org.mockito.Mockito.mock(com.raycoll.app.data.account.AccountManager::class.java)
        kotlinx.coroutines.runBlocking {
            org.mockito.Mockito.`when`(sync.signInWithGoogle("token-1"))
                .thenReturn(SignInResult.Success(Account("uid-1", null)))
        }
        vm = AppSettingsViewModel(
            testPreferences.repository,
            repos.backupRepository,
            google,
            sync,
            repos.store,
            FakeClock()
        )
    }

    @After
    fun teardown() {
        vm.viewModelScope.cancel()
        appScope.cancel()
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
        assertTrue(repos.snapshot().groups.all { it.updatedAt == FakeClock().now() })
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
        repos.store.failWhen = { it is com.raycoll.app.data.firestore.WriteOp.Set && it.path.contains("/decks/") }
        vm.factoryReset()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.SaveFailed, vm.events.first())
        repos.store.failWhen = { false }
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
        org.mockito.Mockito.verify(sync).signInWithGoogle("token-1")
        collector.cancel()
    }
}
