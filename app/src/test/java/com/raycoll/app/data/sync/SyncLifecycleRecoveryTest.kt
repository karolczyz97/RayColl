package com.raycoll.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.IdTokenResult
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncLifecycleRecoveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val repos = VmTestHarness.create(context, clock)
    private val cloud = TestCloud()
    private val user = TestUser()

    private class TestCloud : CloudSync {
        var result: CloudLoad = CloudLoad.Empty
        var hanging = false
        var fresh = true
        var saves = 0
        var saveOk = true
        override val status = MutableStateFlow(SyncStatus.IDLE)
        override suspend fun load(uid: String): CloudLoad {
            if (hanging) awaitCancellation()
            return result
        }
        override suspend fun hasFreshBaseline(uid: String) = fresh
        override suspend fun save(uid: String, data: StoreData): Boolean {
            saves++
            return saveOk
        }
        override fun clearBaseline() {
            status.value = SyncStatus.IDLE
        }
        override fun reportFailure() {
            status.value = SyncStatus.ERROR
        }
    }

    private class TestUser :
        CurrentUser,
        GoogleSignIn {
        var uid: String? = "uid"
        override fun currentUid() = uid
        override val account = MutableStateFlow<Account?>(Account("uid", null))
        override suspend fun requestGoogleIdToken(activityContext: Context) = IdTokenResult.Cancelled
        override suspend fun signInWithIdToken(idToken: String) = SignInResult.Success(Account("uid", null))
        override suspend fun signOut() {
            uid = null
        }
    }

    private fun sync(scope: kotlinx.coroutines.CoroutineScope) = SyncLifecycle(
        user, repos.localStore, repos.deckRepository, repos.namespaceManager,
        BootstrapLoader(user, repos.localStore, repos.namespaceManager, cloud),
        MigrationController(repos.localStore, repos.namespaceManager), cloud, DataMutations(), clock, scope, user
    )

    @After fun close() = repos.db.close()

    @Test
    fun `bootstrap timeout reports an error and retries without a new edit`() = runTest {
        cloud.hanging = true
        val sync = sync(backgroundScope)
        sync.runBootstrap()
        assertEquals(8_000L, testScheduler.currentTime)
        assertEquals(SyncStatus.ERROR, cloud.status.value)
        cloud.hanging = false
        cloud.fresh = false
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, cloud.saves)
        assertTrue(repos.localStore.snapshot().groups.isNotEmpty())
    }

    @Test
    fun `foreground retries pending writes even inside the cloud check interval`() = runTest {
        val sync = sync(backgroundScope)
        cloud.saveOk = false
        sync.runBootstrap()
        runCurrent()
        sync.onAppForeground()
        runCurrent()
        val failures = cloud.saves
        cloud.saveOk = true
        sync.onAppForeground()
        runCurrent()
        assertEquals(failures + 1, cloud.saves)
    }

    @Test
    fun `sign out aborts a hanging foreground reconciliation after five seconds`() = runTest {
        val sync = sync(backgroundScope)
        sync.runBootstrap()
        runCurrent()
        cloud.hanging = true
        cloud.fresh = false
        sync.onAppForeground()
        runCurrent()
        sync.signOut().join()
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(NamespaceManager.GUEST, repos.namespaceManager.current())
    }

    @Test
    fun `local bootstrap failure is a typed sign in error`() = runTest {
        cloud.result = CloudLoad.Loaded(StoreData())
        repos.db.close()
        assertEquals(SignInResult.Failure(SignInError.LOCAL_DATA), sync(backgroundScope).signIn("token").await())
    }

    @Test
    fun `migration failure stays available for retry`() = runTest {
        repos.localStore.seedIfEmpty()
        val deck = repos.localStore.snapshot().groups.first()
        repos.deckRepository.renameDeck(deck.id, "My changed deck")
        val sync = sync(backgroundScope)
        sync.runBootstrap()
        assertTrue(sync.migrationRequired.value)
        repos.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_migration BEFORE INSERT ON decks BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        assertFalse(sync.resolveMigration(MigrationOption.MIGRATE))
        assertTrue(sync.migrationRequired.value)
        repos.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_migration")
        assertTrue(sync.resolveMigration(MigrationOption.MIGRATE))
        assertFalse(sync.migrationRequired.value)
        assertTrue(repos.localStore.snapshot().groups.any { it.name == "My changed deck" })
    }
}
