package com.raycoll.app.data.sync

import android.util.Log
import com.raycoll.app.core.Clock
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.SignInError
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.CloudWriteMode
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.LocalStore
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.di.ApplicationScope
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Najczęściej co tyle sprawdzamy chmurę przy powrocie do aplikacji (szybkie przełączanie aplikacji). */
internal const val FOREGROUND_CLOUD_CHECK_INTERVAL_MS = 30_000L

/**
 * Tyle najwyżej wylogowanie czeka na wysyłkę zaległych zmian (także na wysyłkę rozpoczętą wcześniej).
 * Niewysłane zostają w danych konta na urządzeniu i wyślą się po ponownym zalogowaniu
 * (bootstrap scala je z chmurą).
 */
internal const val SIGN_OUT_PUSH_TIMEOUT_MS = 5_000L

/** Daleko w przeszłości, żeby `now - last` nie przepełniło Long. */
private const val NEVER_CHECKED = Long.MIN_VALUE / 2

/**
 * Cykl życia synchronizacji: start aplikacji i logowanie (scalenie z chmurą),
 * migracja danych gościa, wysyłka lokalnych zmian według [SyncScheduler]
 * oraz domknięcie zaległych zmian przy zejściu w tło.
 */
@Singleton
class SyncLifecycle @Inject constructor(
    private val currentUser: CurrentUser,
    private val localStore: LocalStore,
    private val deckRepository: DeckRepository,
    private val namespaceManager: NamespaceManager,
    private val bootstrapLoader: BootstrapLoader,
    private val migrationController: MigrationController,
    private val cloudSync: CloudSync,
    private val dataMutations: DataMutations,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    private val googleSignIn: GoogleSignIn
) {
    private val scheduler = SyncScheduler(scope, push = ::push)
    private val accountChanges = Mutex()
    private var retryJob: Job? = null

    // Sprawdzanie po powrocie zaczyna się dopiero po pierwszym bootstrapie.
    @Volatile private var bootstrapped = false
    private val lastCloudCheckAt = AtomicLong(NEVER_CHECKED)
    private val _migrationRequired = MutableStateFlow(false)
    val migrationRequired: StateFlow<Boolean> = _migrationRequired.asStateFlow()
    val syncStatus: StateFlow<SyncStatus> get() = cloudSync.status

    /** Operacja na koncie w zakresie aplikacji: anulowanie oczekiwania przez ekran jej nie przerywa. */
    private fun <T> inAccountScope(block: suspend () -> T): Deferred<T> = scope.async {
        accountChanges.withLock { block() }
    }

    fun signIn(idToken: String) = inAccountScope {
        val result = googleSignIn.signInWithIdToken(idToken)
        if (result is SignInResult.Success) {
            try {
                runBootstrap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SyncLifecycle", "Could not prepare local account data", e)
                cloudSync.reportFailure()
                return@inAccountScope SignInResult.Failure(SignInError.LOCAL_DATA)
            }
        }
        result
    }

    fun signOut(): Deferred<Boolean> = inAccountScope {
        try {
            signOutAccount()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SyncLifecycle", "Sign out failed", e)
            cloudSync.reportFailure()
            false
        }
    }

    private suspend fun signOutAccount() {
        retryJob?.cancel()
        withTimeoutOrNull(SIGN_OUT_PUSH_TIMEOUT_MS) { pushNow() }
        // wysyłka, która zaczęła się wcześniej i nadal trzyma zamek, też nie wstrzyma wylogowania
        scheduler.abort()
        scheduler.exclusive {
            try {
                googleSignIn.signOut()
            } finally {
                if (currentUser.currentUid() == null) onSignedOut()
            }
        }
        runBootstrap()
    }

    /** Wołane raz, przy starcie aplikacji. */
    fun start() {
        scope.launch {
            try {
                runBootstrap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SyncLifecycle", "Could not start account", e)
                cloudSync.reportFailure()
            }
        }
        scope.launch {
            dataMutations.events.collect { mode ->
                if (namespaceManager.current() == NamespaceManager.GUEST) return@collect
                when (mode) {
                    CloudWriteMode.DEBOUNCED -> scheduler.enqueueDebounced()
                    CloudWriteMode.STUDY -> scheduler.enqueueStudy()
                    CloudWriteMode.IMMEDIATE -> scheduler.enqueueImmediate()
                }
            }
        }
    }

    /** Start aplikacji i udane logowanie. */
    suspend fun runBootstrap() {
        when (scheduler.exclusive { bootstrapLoader.bootstrap() }) {
            BootstrapResult.Merged, BootstrapResult.SeededFreshAccount ->
                if (currentUser.currentUid() != null) scheduler.enqueueImmediate()

            BootstrapResult.LocalOnly -> Unit

            BootstrapResult.CloudUnavailable -> scheduleRetry()

            BootstrapResult.MigrationRequired -> _migrationRequired.value = true
        }
        bootstrapped = true
        deckRepository.purgeExpiredArchives()
    }

    suspend fun resolveMigration(option: MigrationOption): Boolean = inAccountScope {
        try {
            if (option == MigrationOption.CANCEL) {
                signOutAccount()
            } else {
                if (!_migrationRequired.value) return@inAccountScope true
                scheduler.exclusive { migrationController.execute(option) }
                _migrationRequired.value = false
                pushNow()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SyncLifecycle", "Guest migration failed", e)
            cloudSync.reportFailure()
            false
        }
    }.await()

    private fun onSignedOut() {
        scheduler.cancel()
        cloudSync.clearBaseline()
        namespaceManager.switchToGuest()
        _migrationRequired.value = false
    }

    /**
     * Wysyłka zaległych zmian (też przed wylogowaniem — błąd chmury go nie blokuje).
     * Zwraca false, gdy chmura nie przyjęła zmian; gość nie ma czego wysyłać.
     */
    suspend fun pushNow(): Boolean {
        if (namespaceManager.current() == NamespaceManager.GUEST) return true
        return scheduler.flushImmediately()
    }

    /** Zejście aplikacji w tło. */
    fun onAppBackground() {
        scope.launch { flushIfSignedIn() }
    }

    /** Powrót aplikacji na pierwszy plan. */
    fun onAppForeground(): Job = scope.launch {
        supervisorScope {
            launch { deckRepository.purgeExpiredArchives() }
            launch { refreshIfCloudChanged() }
        }
    }

    /**
     * Zmiany z innego urządzenia: gdy chmura ma nowszą wersję niż nasza baza,
     * scala ją z danymi lokalnymi (zmiany offline zostają) i odsyła wynik. Jeden odczyt
     * dokumentu konta, gdy nic się nie zmieniło. Nie równolegle z wysyłką tego samego konta.
     */
    private suspend fun refreshIfCloudChanged() {
        val uid = namespaceManager.current()
        if (uid == NamespaceManager.GUEST) return
        if (!bootstrapped || _migrationRequired.value) return
        val now = clock.now()
        val last = lastCloudCheckAt.get()
        if (now - last < FOREGROUND_CLOUD_CHECK_INTERVAL_MS || !lastCloudCheckAt.compareAndSet(last, now)) {
            scheduler.flush()
            return
        }
        val merged = scheduler.exclusive {
            namespaceManager.current() == uid && !cloudSync.hasFreshBaseline(uid) && reconcileWithCloud(uid)
        }
        // scalony stan może zawierać zmiany zrobione offline — wysyłka różnic (pusta, gdy ich nie ma)
        if (merged) {
            scheduler.enqueueImmediate()
        } else if (!scheduler.flush()) {
            scheduleRetry()
        }
    }

    private suspend fun flushIfSignedIn() {
        if (namespaceManager.current() != NamespaceManager.GUEST && !scheduler.flush()) scheduleRetry()
    }

    private suspend fun push(): Boolean {
        val uid = namespaceManager.current()
        if (uid == NamespaceManager.GUEST || _migrationRequired.value) return true
        // ktoś zmienił chmurę albo start był offline: najpierw scal, inaczej wysłalibyśmy nieaktualny stan
        if (!cloudSync.hasFreshBaseline(uid) && !reconcileWithCloud(uid)) {
            scheduleRetry()
            return false
        }
        return cloudSync.save(uid, localStore.snapshot()).also { if (!it) scheduleRetry() }
    }

    /** Zwraca false przy awarii chmury lub oczekiwaniu na wybór migracji konta. */
    private suspend fun reconcileWithCloud(uid: String): Boolean = when (val cloud = cloudSync.load(uid)) {
        is CloudLoad.Loaded -> {
            localStore.mergeWithCloud(cloud.data, cloud.positionsComplete)
            true
        }

        CloudLoad.Empty -> {
            if (bootstrapLoader.bootstrapFreshAccount() == BootstrapResult.MigrationRequired) {
                _migrationRequired.value = true
                false
            } else {
                true
            }
        }

        CloudLoad.Failed -> {
            scheduleRetry()
            false
        }
    }

    /** Krótka awaria nie wymaga nowej edycji; po trzech próbach zostaje widoczny błąd. */
    @Synchronized
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val uid = currentUser.currentUid() ?: return
        retryJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            for (waitMs in listOf(2_000L, 4_000L, 8_000L)) {
                delay(waitMs)
                if (namespaceManager.current() != uid || _migrationRequired.value) return@launch
                if (pushNow()) return@launch
            }
        }.also { it.start() }
    }
}
