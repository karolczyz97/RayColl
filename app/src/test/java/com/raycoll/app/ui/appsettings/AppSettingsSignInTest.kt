package com.raycoll.app.ui.appsettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.IdTokenResult
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupResult
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertFalse
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
        override val account: StateFlow<Account?> = MutableStateFlow(null)
        override suspend fun requestGoogleIdToken(activityContext: Context) = tokenRequest.await()

        // ustawienia logują tokenem przez AccountManager, nie przez ten interfejs
        override suspend fun signInWithIdToken(idToken: String): SignInResult = error("not used by settings")
        override suspend fun signOut() = Unit
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val google = FakeGoogle()
    private lateinit var repos: TestRepos
    private lateinit var vm: AppSettingsViewModel
    private lateinit var sync: com.raycoll.app.data.account.AccountManager

    private val main = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(main)
        repos = VmTestHarness.create(context, FakeClock())
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
        org.mockito.Mockito.verify(sync, org.mockito.Mockito.never())
            .signInWithGoogle(org.mockito.ArgumentMatchers.anyString())
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

    @Test
    fun `successful sign out emits no failure and unlocks the screen`() = runTest(main) {
        org.mockito.Mockito.`when`(sync.signOut()).thenReturn(true)
        vm.signOut()
        advanceUntilIdle()
        org.mockito.Mockito.verify(sync).signOut()
        assertFalse(vm.uiState.value.signingOut)
        // pierwsze zdarzenie po wylogowaniu pochodzi już z synchronizacji — SignOutFailed nie czekało w kolejce
        vm.syncNow()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.SyncFinished(true), vm.events.first())
    }

    @Test
    fun `refused sign out emits SignOutFailed`() = runTest(main) {
        org.mockito.Mockito.`when`(sync.signOut()).thenReturn(false)
        vm.signOut()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.SignOutFailed, vm.events.first())
        assertFalse(vm.uiState.value.signingOut)
    }

    @Test
    fun `manual sync reports whether pending writes reached the server`() = runTest(main) {
        vm.syncNow()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.SyncFinished(true), vm.events.first())
        repos.store.pendingWritesConfirmed = false
        vm.syncNow()
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.SyncFinished(false), vm.events.first())
    }

    @Test
    fun `import reports the backup result`() = runTest(main) {
        val invalid = File(folder.root, "invalid.json").apply { writeText("{}") }
        vm.importBackup(Uri.fromFile(invalid))
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.BackupImported(BackupResult.INVALID_BACKUP), vm.events.first())

        val exported = File(folder.root, "backup.json")
        vm.exportBackup(Uri.fromFile(exported))
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.BackupExported(BackupResult.SUCCESS), vm.events.first())
        vm.importBackup(Uri.fromFile(exported))
        advanceUntilIdle()
        assertEquals(AppSettingsEvent.BackupImported(BackupResult.SUCCESS), vm.events.first())
    }
}
