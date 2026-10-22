package com.raycoll.app.data.account

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.raycoll.app.R
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.GoogleCredential
import com.raycoll.app.data.remote.auth.LinkResult
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.repository.Cleanup
import com.raycoll.app.di.ApplicationScope
import com.raycoll.app.domain.merge.guestHasData
import com.raycoll.app.domain.model.StoreData
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class GuestDataChoice { MOVE_TO_ACCOUNT, KEEP_ON_DEVICE, DELETE, CANCEL }
sealed interface AccountState {
    data object Starting : AccountState
    data object NeedsInternet : AccountState
    data class Ready(val uid: String, val isGuest: Boolean, val email: String?) : AccountState
}

/** Ile przy zmianie konta czekamy, aż zapisy dotrą do serwera. */
private const val PENDING_WRITES_TIMEOUT_MS = 15_000L

@Singleton
class AccountManager @Inject constructor(
    private val auth: AuthRepository,
    private val store: DocumentStore,
    private val backups: Provider<BackupRepository>,
    private val cleanup: Cleanup,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) : CurrentAccount {
    private val _uid = MutableStateFlow<String?>(null)
    override val uid: StateFlow<String?> = _uid.asStateFlow()
    private val _state = MutableStateFlow<AccountState>(AccountState.Starting)
    val state: StateFlow<AccountState> = _state.asStateFlow()
    private val _choiceRequired = MutableStateFlow(false)
    val choiceRequired: StateFlow<Boolean> = _choiceRequired.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val operations = Mutex()
    private var token: String? = null
    private var cleanupUid: String? = null
    private var cleanupJob: Job? = null
    private val backup get() = backups.get()

    fun start() {
        scope.launch { retryStart() }
    }

    suspend fun retryStart() = operation {
        _state.value = AccountState.Starting
        prepareOrNeedsInternet()
    }

    private suspend fun prepareOrNeedsInternet() {
        try {
            prepareAccount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountManager", "Account startup failed", e)
            _uid.value = null
            _state.value = AccountState.NeedsInternet
        }
    }

    private suspend fun prepareAccount() {
        val previous = auth.currentUser
        val user = previous ?: auth.signInAnonymously()
        val pending = backup.loadPrivate(PENDING)
        if (pending != null) {
            if (user.isAnonymous) {
                backup.restoreGuest(pending, user.uid)
                // Plik znika dopiero po potwierdzeniu wysłania. Po przerwaniu pozostaje do odzyskania.
                check(store.awaitPendingWrites(PENDING_WRITES_TIMEOUT_MS)) { "Pending import has not reached server" }
                backup.deletePrivate(PENDING)
            } else {
                joinPending(pending, user.uid)
            }
        } else if (previous == null) {
            val stash = backup.loadPrivate(STASH)
            if (stash != null) {
                backup.restoreGuest(stash, user.uid)
                // Po limicie czasu dane czekają w trwałej kolejce Firestore nowego gościa i wyślą się same.
                // Plik i tak znika: zostawiony wróciłby przy kolejnym wylogowaniu, także po „Usuń”.
                store.awaitPendingWrites(PENDING_WRITES_TIMEOUT_MS)
                backup.deletePrivate(STASH)
            } else {
                backup.writeSeed(user.uid, Locale.getDefault().language)
            }
        }
        ready()
    }

    /**
     * Dołączenie dodaje aktywność gościa do dni konta, więc nie może wykonać się drugi raz. Po limicie
     * czasu zapisy czekają w trwałej kolejce Firestore tego konta i wyślą się same, a plik i tak znika.
     */
    private suspend fun joinPending(data: StoreData, uid: String) {
        backup.joinGuest(data, uid, context.getString(R.string.auth_from_phone_suffix))
        store.awaitPendingWrites(PENDING_WRITES_TIMEOUT_MS)
        backup.deletePrivate(PENDING)
    }

    private fun ready() {
        val user = checkNotNull(auth.currentUser)
        _uid.value = user.uid
        _state.value = AccountState.Ready(user.uid, user.isAnonymous, user.email)
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
        if (guestHasData(data.groups, data.activityHeatmap, data.studyModes)) {
            _choiceRequired.value = true
            SignInResult.ChoiceRequired
        } else {
            signIn(GuestDataChoice.DELETE, tryLink = true)
        }
    }

    suspend fun resolveChoice(choice: GuestDataChoice): Boolean = operation {
        if (choice == GuestDataChoice.CANCEL) {
            token = null
            _choiceRequired.value = false
            return@operation true
        }
        when (signIn(choice, tryLink = choice == GuestDataChoice.MOVE_TO_ACCOUNT)) {
            is SignInResult.Success -> {
                _choiceRequired.value = false
                true
            }

            else -> false
        }
    }

    private suspend fun signIn(choice: GuestDataChoice, tryLink: Boolean): SignInResult {
        val idToken = token ?: return SignInResult.Failure(SignInError.NO_TOKEN)
        try {
            var collision: GoogleCredential? = null
            if (tryLink) {
                when (val link = auth.linkWithGoogle(idToken)) {
                    LinkResult.Linked -> {
                        token = null
                        ready()
                        val user = checkNotNull(auth.currentUser)
                        return SignInResult.Success(Account(user.uid, user.email), true)
                    }

                    is LinkResult.Collision -> collision = link.credential
                }
            }
            val credential = collision ?: auth.googleCredential(idToken)
            val data = leaveGuest(choice)
            return enterGoogleAccount(credential, choice, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountManager", "Account change failed", e)
            // Jeśli konto zostało przełączone, plik pending zostaje do dokończenia przy starcie.
            if (auth.currentUser == null) {
                prepareOrNeedsInternet()
            } else {
                ready()
            }
            return SignInResult.Failure(SignInError.LOCAL_DATA)
        }
    }

    /** Odkłada dane gościa według [choice], usuwa jego konto i wylogowuje; zwraca odłożone dane. */
    private suspend fun leaveGuest(choice: GuestDataChoice): StoreData {
        val guestUid = checkNotNull(auth.currentUser).uid
        val data = backup.snapshot(guestUid)
        when (choice) {
            GuestDataChoice.MOVE_TO_ACCOUNT -> backup.savePrivate(PENDING, data)
            GuestDataChoice.KEEP_ON_DEVICE -> backup.savePrivate(STASH, data)
            else -> Unit
        }
        cleanupJob?.cancel()
        backup.deleteGuest(guestUid)
        store.awaitPendingWrites(PENDING_WRITES_TIMEOUT_MS)
        auth.deleteCurrentUser()
        // Nawet gdy usunięcie Auth odmówiło, stary gość nie może pozostać aktywny.
        cleanupJob?.cancel()
        auth.signOut()
        _uid.value = null
        _state.value = AccountState.Starting
        return data
    }

    /** Logowanie na konto Google i dołączenie odłożonych danych; nieudane logowanie wraca do gościa. */
    private suspend fun enterGoogleAccount(
        credential: GoogleCredential,
        choice: GuestDataChoice,
        data: StoreData
    ): SignInResult {
        val result = auth.signInWithCredential(credential)
        if (result is SignInResult.Success) {
            if (choice == GuestDataChoice.MOVE_TO_ACCOUNT) {
                joinPending(data, result.account.uid)
            } else if (result.isNewUser) {
                backup.writeSeed(result.account.uid, Locale.getDefault().language)
            }
            token = null
            ready()
            return result
        }
        prepareAccount()
        return result
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
            _uid.value = null
            _state.value = AccountState.Starting
            prepareAccount()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountManager", "Sign-out failed", e)
            _state.value = AccountState.NeedsInternet
            false
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): T = scope.async {
        operations.withLock {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }.await()

    companion object {
        const val STASH = "guest_stash.json"
        const val PENDING = "pending_join.json"
    }
}
