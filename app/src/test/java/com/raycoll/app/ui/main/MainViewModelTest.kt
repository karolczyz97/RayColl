package com.raycoll.app.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.account.AccountState
import com.raycoll.app.data.account.GuestDataChoice
import com.raycoll.app.testing.PreferencesRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    @get:Rule val preferences = PreferencesRule()
    private val main = StandardTestDispatcher()
    private val sync = mock(AccountManager::class.java)
    private val account = MutableStateFlow<AccountState>(AccountState.Starting)
    private lateinit var vm: MainViewModel

    @Before fun setup() {
        Dispatchers.setMain(main)
        `when`(sync.choiceRequired).thenReturn(MutableStateFlow(true))
        `when`(sync.state).thenReturn(account)
        vm = vm(SavedStateHandle())
    }

    private fun vm(savedState: SavedStateHandle) = MainViewModel(
        preferences.repository,
        sync,
        com.raycoll.app.testing.InMemoryDocumentStore(com.raycoll.app.testing.FakeClock()),
        savedState
    )

    @After fun close() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `double click starts one migration and a failed migration can be retried`() = runTest(main) {
        `when`(sync.resolveChoice(GuestDataChoice.MOVE_TO_ACCOUNT)).thenReturn(false)
        vm.resolveMigration(GuestDataChoice.MOVE_TO_ACCOUNT)
        vm.resolveMigration(GuestDataChoice.KEEP_ON_DEVICE)
        assertTrue(vm.migrating.value)
        runCurrent()
        assertFalse(vm.migrating.value)
        assertTrue(vm.migrationFailed.value)
        verify(sync).resolveChoice(GuestDataChoice.MOVE_TO_ACCOUNT)
        verify(sync, never()).resolveChoice(GuestDataChoice.KEEP_ON_DEVICE)
        `when`(sync.resolveChoice(GuestDataChoice.MOVE_TO_ACCOUNT)).thenReturn(true)
        vm.resolveMigration(GuestDataChoice.MOVE_TO_ACCOUNT)
        runCurrent()
        assertFalse(vm.migrationFailed.value)
    }

    @Test fun `window notices a sign-in to another account but not a restart of its own`() = runTest(main) {
        val savedState = SavedStateHandle()
        val window = vm(savedState)
        account.value = AccountState.Ready("guest", isGuest = true, email = null)
        runCurrent()
        account.value = AccountState.Starting
        runCurrent()
        assertFalse(window.accountChanged.value)

        // okno odtworzone po śmierci procesu pamięta konto, z którym je otwarto
        val restored = vm(savedState)
        account.value = AccountState.Ready("guest", isGuest = true, email = null)
        runCurrent()
        assertFalse(restored.accountChanged.value)

        account.value = AccountState.Ready("google", isGuest = false, email = "a@b.c")
        runCurrent()
        assertTrue(window.accountChanged.value)
        assertTrue(restored.accountChanged.value)
        restored.viewModelScope.cancel()
        window.viewModelScope.cancel()
    }

    @Test fun `window shows the screen only when the account is ready`() = runTest(main) {
        val window = vm(SavedStateHandle())
        backgroundScope.launch { window.accountGate.collect {} }
        runCurrent()
        assertEquals(AccountGate.Starting, window.accountGate.value)
        assertTrue(window.accountGate.value.showsLoading)

        account.value = AccountState.NeedsInternet
        runCurrent()
        assertEquals(AccountGate.NeedsInternet, window.accountGate.value)
        assertFalse(window.accountGate.value.showsLoading)

        account.value = AccountState.Ready("google", isGuest = false, email = "a@b.c")
        runCurrent()
        assertEquals(AccountGate.Open, window.accountGate.value)
        window.viewModelScope.cancel()
    }

    @Test fun `new window of a Google account starts by waiting for data, a guest window does not`() {
        account.value = AccountState.Ready("google", isGuest = false, email = "a@b.c")
        val google = vm(SavedStateHandle())
        assertEquals(AccountGate.WaitingForData(offline = false), google.accountGate.value)
        assertTrue(google.accountGate.value.showsLoading)

        account.value = AccountState.Ready("guest", isGuest = true, email = null)
        val guest = vm(SavedStateHandle())
        assertEquals(AccountGate.Open, guest.accountGate.value)
        google.viewModelScope.cancel()
        guest.viewModelScope.cancel()
    }
}
