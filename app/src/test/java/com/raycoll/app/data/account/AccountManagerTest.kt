package com.raycoll.app.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repos = VmTestHarness.create(context, FakeClock())
    private val auth = mock(AuthRepository::class.java)
    private var current: FirebaseUser? = user("uid-1", true)

    private fun user(id: String, guest: Boolean) = mock(FirebaseUser::class.java).also {
        `when`(it.uid).thenReturn(id)
        `when`(it.isAnonymous).thenReturn(guest)
    }

    private suspend fun configure(existing: Boolean) {
        `when`(auth.currentUser).thenAnswer { current }
        `when`(auth.linkWithGoogle("token")).thenAnswer {
            if (existing) throw FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "used")
            current = user("uid-1", false)
            mock(AuthResult::class.java)
        }
        `when`(
            auth.signInWithCredential(
                any(
                    AuthCredential::class.java
                ) ?: com.google.firebase.auth.GoogleAuthProvider.getCredential("token", null)
            )
        ).thenAnswer {
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
        val manager =
            AccountManager(
                auth,
                repos.store,
                Provider {
                    repos.backupRepository
                },
                com.raycoll.app.data.repository.Cleanup(repos.store, FakeClock()),
                context,
                backgroundScope
            )
        manager.retryStart()
        assertEquals(SignInResult.ChoiceRequired, manager.signInWithGoogle("token"))
        assertTrue(manager.resolveChoice(GuestDataChoice.CANCEL))
        assertEquals(before, repos.snapshot())
        assertEquals("uid-1", manager.uid.value)
        assertFalse(manager.choiceRequired.value)
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
        val manager =
            AccountManager(
                auth,
                repos.store,
                Provider {
                    repos.backupRepository
                },
                com.raycoll.app.data.repository.Cleanup(repos.store, FakeClock()),
                context,
                backgroundScope
            )
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
        val manager =
            AccountManager(
                auth,
                waiting,
                Provider {
                    repos.backupRepository
                },
                com.raycoll.app.data.repository.Cleanup(waiting, FakeClock()),
                context,
                backgroundScope
            )
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
            mock(AuthResult::class.java).also { `when`(it.user).thenReturn(current) }
        }
        val waiting = object : DocumentStore by repos.store {
            override suspend fun awaitPendingWrites(timeoutMs: Long) = false
        }
        val manager =
            AccountManager(
                auth,
                waiting,
                Provider {
                    repos.backupRepository
                },
                com.raycoll.app.data.repository.Cleanup(waiting, FakeClock()),
                context,
                backgroundScope
            )
        manager.retryStart()
        assertTrue(manager.state.value is AccountState.Ready)
        assertNull(repos.backupRepository.loadPrivate(AccountManager.STASH))
        assertEquals(kept, repos.backupRepository.snapshot("new-guest"))
    }

    @Test fun `keep writes stash before guest is deleted and seeds a new Google account`() = runTest {
        configure(false)
        repos.deckRepository.addDeckWithCards("Own", listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))
        val before = repos.snapshot()
        val manager =
            AccountManager(
                auth,
                repos.store,
                Provider {
                    repos.backupRepository
                },
                com.raycoll.app.data.repository.Cleanup(repos.store, FakeClock()),
                context,
                backgroundScope
            )
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
        val manager =
            AccountManager(
                auth,
                repos.store,
                Provider {
                    repos.backupRepository
                },
                com.raycoll.app.data.repository.Cleanup(repos.store, FakeClock()),
                context,
                backgroundScope
            )
        manager.retryStart()
        assertTrue(manager.signInWithGoogle("token") is SignInResult.Success)
        assertFalse(manager.choiceRequired.value)
        assertEquals(before, repos.snapshot())
        assertEquals("uid-1", manager.uid.value)
    }
}
