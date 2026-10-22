package com.raycoll.app.data.account

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.AuthCredential
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.AuthUser
import com.raycoll.app.data.remote.auth.GoogleCredential
import com.raycoll.app.data.remote.auth.LinkResult
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.Cleanup
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class AccountManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repos = VmTestHarness.create(context, FakeClock())
    private val auth = mock(AuthRepository::class.java)
    private var current: AuthUser? = user("uid-1", true)
    private val credential = GoogleCredential(mock(AuthCredential::class.java))

    private fun user(id: String, guest: Boolean) = AuthUser(id, null, guest)

    private fun TestScope.manager(store: DocumentStore = repos.store, scope: CoroutineScope = backgroundScope) =
        AccountManager(auth, store, Provider { repos.backupRepository }, Cleanup(store, FakeClock()), context, scope)

    private suspend fun configure(existing: Boolean) {
        `when`(auth.currentUser).thenAnswer { current }
        `when`(auth.googleCredential("token")).thenReturn(credential)
        `when`(auth.linkWithGoogle("token")).thenAnswer {
            if (existing) return@thenAnswer LinkResult.Collision(credential)
            current = user("uid-1", false)
            LinkResult.Linked
        }
        `when`(auth.signInWithCredential(any(GoogleCredential::class.java) ?: credential)).thenAnswer {
            current = user("google", false)
            SignInResult.Success(Account("google", "test@example.com"), !existing)
        }
        `when`(auth.signOut()).thenAnswer {
            current = null
            Unit
        }
        `when`(auth.deleteCurrentUser()).thenAnswer {
            current = null
            Unit
        }
    }

    @Test fun `cancel keeps guest account and all data`() = runTest {
        configure(true)
        repos.deckRepository.addDeckWithCards("Own", listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))
        val before = repos.snapshot()
        val manager = manager()
        manager.retryStart()
        assertEquals(SignInResult.ChoiceRequired, manager.signInWithGoogle("token"))
        assertTrue(manager.resolveChoice(GuestDataChoice.CANCEL))
        assertEquals(before, repos.snapshot())
        assertEquals("uid-1", manager.uid.value)
        assertFalse(manager.choiceRequired.value)
    }

    @Test fun `failed sign in and offline guest recovery leave a retryable state with pending data`() = runTest {
        configure(true)
        repos.deckRepository.addDeckWithCards("Own", listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))
        doReturn(SignInResult.Failure(SignInError.LOGIN_FAILED)).`when`(auth).signInWithCredential(
            any(GoogleCredential::class.java) ?: credential
        )
        `when`(auth.signInAnonymously()).thenThrow(IllegalStateException("offline"))
        val manager = manager()
        manager.retryStart()
        assertEquals(SignInResult.ChoiceRequired, manager.signInWithGoogle("token"))

        assertFalse(manager.resolveChoice(GuestDataChoice.MOVE_TO_ACCOUNT))

        assertEquals(AccountState.NeedsInternet, manager.state.value)
        assertNull(manager.uid.value)
        val pending = repos.backupRepository.loadPrivate(AccountManager.PENDING)
        assertNotNull(pending)
        doAnswer {
            current = user("recovered-guest", true)
            current
        }.`when`(auth).signInAnonymously()
        manager.retryStart()
        assertTrue(manager.state.value is AccountState.Ready)
        assertEquals("recovered-guest", manager.uid.value)
        assertEquals(pending, repos.backupRepository.snapshot("recovered-guest"))
        assertNull(repos.backupRepository.loadPrivate(AccountManager.PENDING))
    }

    @Test fun `move to existing account cleans guest and appends data with progress`() = runTest {
        configure(true)
        val id = repos.deckRepository.addDeckWithCards(
            "Own",
            listOf("en", "pl"),
            listOf("A", "B"),
            listOf(listOf("q", "a"))
        )
        val card = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.reviewCard(id, card.id, 3)
        val before = repos.snapshot()
        val manager = manager()
        manager.retryStart()
        assertEquals(SignInResult.ChoiceRequired, manager.signInWithGoogle("token"))
        assertTrue(manager.resolveChoice(GuestDataChoice.MOVE_TO_ACCOUNT))
        assertTrue(repos.backupRepository.snapshot("uid-1").groups.isEmpty())
        assertEquals(before, repos.backupRepository.snapshot("google"))
        assertNull(repos.backupRepository.loadPrivate(AccountManager.PENDING))
        assertEquals("google", manager.uid.value)
    }

    @Test fun `move finishes once when the upload is still waiting for internet`() = runTest {
        configure(true)
        repos.deckRepository.addDeckWithCards("Own", listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))
        repos.setActivity("2026-10-15", 2)
        val waiting = object : DocumentStore by repos.store {
            override suspend fun awaitPendingWrites(timeoutMs: Long) = false
        }
        val manager = manager(waiting)
        manager.retryStart()
        assertEquals(SignInResult.ChoiceRequired, manager.signInWithGoogle("token"))
        assertTrue(manager.resolveChoice(GuestDataChoice.MOVE_TO_ACCOUNT))
        assertFalse(manager.choiceRequired.value)
        assertNull(repos.backupRepository.loadPrivate(AccountManager.PENDING))
        manager.retryStart()
        assertEquals(2, repos.backupRepository.snapshot("google").activityHeatmap["2026-10-15"])
    }

    @Test fun `kept guest data returns once when the upload is still waiting for internet`() = runTest {
        configure(false)
        repos.deckRepository.addDeckWithCards("Own", listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))
        val kept = repos.snapshot()
        repos.backupRepository.savePrivate(AccountManager.STASH, kept)
        current = null
        `when`(auth.signInAnonymously()).thenAnswer {
            current = user("new-guest", true)
            current
        }
        val waiting = object : DocumentStore by repos.store {
            override suspend fun awaitPendingWrites(timeoutMs: Long) = false
        }
        val manager = manager(waiting)
        manager.retryStart()
        assertTrue(manager.state.value is AccountState.Ready)
        assertNull(repos.backupRepository.loadPrivate(AccountManager.STASH))
        assertEquals(kept, repos.backupRepository.snapshot("new-guest"))
    }

    @Test fun `keep writes stash before guest is deleted and seeds a new Google account`() = runTest {
        configure(false)
        repos.deckRepository.addDeckWithCards("Own", listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))
        val before = repos.snapshot()
        val manager = manager()
        manager.retryStart()
        manager.signInWithGoogle("token")
        assertTrue(manager.resolveChoice(GuestDataChoice.KEEP_ON_DEVICE))
        assertEquals(before, repos.backupRepository.loadPrivate(AccountManager.STASH))
        assertTrue(repos.backupRepository.snapshot("uid-1").groups.isEmpty())
        assertEquals(2, repos.backupRepository.snapshot("google").groups.size)
        verify(auth, never()).linkWithGoogle("token")
        repos.backupRepository.deletePrivate(AccountManager.STASH)
    }

    @Test fun `unmodified guest links silently to new Google account`() = runTest {
        configure(false)
        repos.seed()
        val before = repos.snapshot()
        val manager = manager()
        manager.retryStart()
        assertTrue(manager.signInWithGoogle("token") is SignInResult.Success)
        assertFalse(manager.choiceRequired.value)
        assertEquals(before, repos.snapshot())
        assertEquals("uid-1", manager.uid.value)
    }

    private fun network(validated: Boolean) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
    }

    @Test fun `sign out without a validated network returns false and keeps the account`() = runTest {
        configure(false)
        current = user("google", false)
        val manager = manager()
        manager.retryStart()
        val before = manager.state.value
        network(validated = false)

        assertFalse(manager.signOut())

        assertEquals(AccountState.Ready("google", false, null), before)
        assertEquals(before, manager.state.value)
        assertEquals("google", manager.uid.value)
        verify(auth, never()).signOut()
    }

    @Test fun `sign out with a validated network starts a fresh guest`() = runTest {
        configure(false)
        current = user("google", false)
        `when`(auth.signInAnonymously()).thenAnswer {
            current = user("new-guest", true)
            current
        }
        val manager = manager()
        manager.retryStart()
        network(validated = true)

        assertTrue(manager.signOut())

        assertEquals(AccountState.Ready("new-guest", true, null), manager.state.value)
        assertEquals("new-guest", manager.uid.value)
    }

    @Test fun `sign out whose guest start fails ends in NeedsInternet`() = runTest {
        configure(false)
        current = user("google", false)
        `when`(auth.signInAnonymously()).thenThrow(IllegalStateException("offline"))
        val manager = manager()
        manager.retryStart()
        network(validated = true)

        assertFalse(manager.signOut())

        verify(auth).signOut()
        assertEquals(AccountState.NeedsInternet, manager.state.value)
        assertNull(manager.uid.value)
    }
}
