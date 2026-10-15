package com.raycoll.app.data.account

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.raycoll.app.R
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.repository.Cleanup
import com.raycoll.app.di.ApplicationScope
import com.raycoll.app.domain.merge.getGuestHasData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class GuestDataChoice { MOVE_TO_ACCOUNT, KEEP_ON_DEVICE, DELETE, CANCEL }
sealed interface AccountState {
    data object Starting : AccountState
    data object NeedsInternet : AccountState
    data class Ready(val uid: String, val isGuest: Boolean, val email: String?) : AccountState
}

@Singleton
class AccountManager @Inject constructor(
    private val auth: AuthRepository,
    private val store: DocumentStore,
    private val backups: Provider<BackupRepository>,
    private val cleanup: Cleanup,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) : CurrentAccount {
    override val uid = MutableStateFlow<String?>(null)
    val state = MutableStateFlow<AccountState>(AccountState.Starting)
    val choiceRequired = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    private val operations = Mutex()
    private var token: String? = null
    private var cleanupUid: String? = null
    private var cleanupJob: Job? = null
    private val backup get() = backups.get()

    fun start() {
        scope.launch { retryStart() }
    }

    suspend fun retryStart() = operation {
        state.value = AccountState.Starting
        try {
            prepareAccount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountManager", "Account startup failed", e)
            uid.value = null
            state.value = AccountState.NeedsInternet
        }
    }

    private suspend fun prepareAccount() {
        val previous = auth.currentUser
        val user = previous ?: checkNotNull(auth.signInAnonymously().user)
        val pending = backup.loadPrivate(PENDING)
        if (pending != null) {
            if (user.isAnonymous) {
                backup.restoreGuest(pending, user.uid)
            } else {
                backup.joinGuest(pending, user.uid, context.getString(R.string.auth_from_phone_suffix))
            }
            // Plik znika dopiero po potwierdzeniu wysłania. Po przerwaniu pozostaje do odzyskania.
            check(store.awaitPendingWrites(15_000)) { "Pending import has not reached server" }
            backup.deletePrivate(PENDING)
        } else if (previous == null) {
            val stash = backup.loadPrivate(STASH)
            if (stash != null) {
                backup.restoreGuest(stash, user.uid)
                check(store.awaitPendingWrites(15_000)) { "Guest restore has not reached server" }
                backup.deletePrivate(STASH)
            } else {
                backup.writeSeed(user.uid, Locale.getDefault().language)
            }
        }
        ready()
    }

    private fun ready() {
        val user = checkNotNull(auth.currentUser)
        uid.value = user.uid
        state.value = AccountState.Ready(user.uid, user.isAnonymous, user.email)
        if (cleanupUid != user.uid) {
            cleanupJob?.cancel()
            cleanupUid = user.uid
            cleanupJob = scope.launch { cleanup.run(user.uid) }
        }
    }

    suspend fun signInWithGoogle(idToken: String): SignInResult = operation {
        token = idToken
        val user = auth.currentUser ?: return@operation SignInResult.Failure(SignInError.LOGIN_FAILED)
        if (!user.isAnonymous) return@operation SignInResult.Success(Account(user.uid, user.email))
        val data = backup.snapshot(user.uid)
        if (getGuestHasData(data.groups, data.activityHeatmap, data.studyModes)) {
            choiceRequired.value = true
            SignInResult.ChoiceRequired
        } else {
            signIn(GuestDataChoice.DELETE, tryLink = true)
        }
    }

    suspend fun resolveChoice(choice: GuestDataChoice): Boolean = operation {
        if (choice == GuestDataChoice.CANCEL) {
            token = null
            choiceRequired.value = false
            return@operation true
        }
        when (signIn(choice, tryLink = choice == GuestDataChoice.MOVE_TO_ACCOUNT)) {
            is SignInResult.Success -> {
                choiceRequired.value = false
                true
            }

            else -> false
        }
    }

    private suspend fun signIn(choice: GuestDataChoice, tryLink: Boolean): SignInResult {
        val idToken = token ?: return SignInResult.Failure(SignInError.NO_TOKEN)
        var credential = GoogleAuthProvider.getCredential(idToken, null)
        try {
            if (tryLink) {
                try {
                    auth.linkWithGoogle(idToken)
                    token = null
                    ready()
                    val user = checkNotNull(auth.currentUser)
                    return SignInResult.Success(Account(user.uid, user.email), true)
                } catch (collision: FirebaseAuthUserCollisionException) {
                    credential = collision.updatedCredential ?: credential
                }
            }
            val guestUid = checkNotNull(auth.currentUser).uid
            val data = backup.snapshot(guestUid)
            when (choice) {
                GuestDataChoice.MOVE_TO_ACCOUNT -> backup.savePrivate(PENDING, data)
                GuestDataChoice.KEEP_ON_DEVICE -> backup.savePrivate(STASH, data)
                else -> Unit
            }
            cleanupJob?.cancel()
            backup.deleteGuest(guestUid)
            store.awaitPendingWrites(15_000)
            auth.deleteCurrentUser()
            // Nawet gdy usunięcie Auth odmówiło, stary gość nie może pozostać aktywny.
            cleanupJob?.cancel()
            auth.signOut()
            uid.value = null
            state.value = AccountState.Starting
            val result = auth.signInWithCredential(credential)
            if (result is SignInResult.Success) {
                if (choice == GuestDataChoice.MOVE_TO_ACCOUNT) {
                    backup.joinGuest(data, result.account.uid, context.getString(R.string.auth_from_phone_suffix))
                    check(store.awaitPendingWrites(15_000)) { "Guest import is waiting for internet" }
                    backup.deletePrivate(PENDING)
                } else if (result.isNewUser) {
                    backup.writeSeed(result.account.uid, Locale.getDefault().language)
                }
                token = null
                ready()
                return result
            }
            prepareAccount()
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountManager", "Account change failed", e)
            // Jeśli konto zostało przełączone, plik pending zostaje do dokończenia przy starcie.
            if (auth.currentUser == null) {
                runCatching { prepareAccount() }
            } else {
                ready()
            }
            return SignInResult.Failure(SignInError.LOCAL_DATA)
        }
    }

    suspend fun signOut(): Boolean = operation {
        val network = context.getSystemService(ConnectivityManager::class.java)
        if (network.getNetworkCapabilities(
                network.activeNetwork
            )?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) !=
            true
        ) {
            return@operation false
        }
        try {
            cleanupJob?.cancel()
            auth.signOut()
            uid.value = null
            state.value = AccountState.Starting
            prepareAccount()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountManager", "Sign-out failed", e)
            state.value = AccountState.NeedsInternet
            false
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): T = scope.async {
        operations.withLock {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }.await()

    companion object {
        const val STASH = "guest_stash.json"
        const val PENDING = "pending_join.json"
    }
}
