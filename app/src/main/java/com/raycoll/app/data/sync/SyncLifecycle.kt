package com.raycoll.app.data.sync

import com.raycoll.app.core.Clock
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.CloudWriteMode
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.LocalStore
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.di.ApplicationScope
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.playback.StudySessionHolder
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

/**
 * Cykl życia synchronizacji: start aplikacji i logowanie (scalenie z chmurą),
 * migracja danych gościa, wysyłka lokalnych zmian według [SyncScheduler]
 * oraz domknięcie zaległych zmian na końcu sesji nauki i przy zejściu w tło.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SyncLifecycle @Inject constructor(
    private val authRepository: CurrentUser,
    private val localStore: LocalStore,
    private val deckRepository: DeckRepository,
    private val namespaceManager: NamespaceManager,
    private val bootstrapLoader: BootstrapLoader,
    private val migrationController: MigrationController,
    private val syncEngine: CloudSync,
    private val dataMutations: DataMutations,
    private val studySessionHolder: StudySessionHolder,
    private val reviewWrites: ReviewWriteQueue,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    private val googleSignIn: GoogleSignIn
) {
    private val scheduler = SyncScheduler(scope, push = ::push)
    private val accountChanges = Mutex()

    /** Anulowanie oczekiwania przez ekran nie przerywa logowania ani bootstrapu. */
    fun signIn(idToken: String) = scope.async {
        accountChanges.withLock {
            googleSignIn.signInWithIdToken(idToken).also { result ->
                if (result is SignInResult.Success) runBootstrap()
            }
        }
    }

    fun signOut() = scope.launch {
        accountChanges.withLock { signOutAccount() }
    }

    private suspend fun signOutAccount() {
        withTimeoutOrNull(SIGN_OUT_PUSH_TIMEOUT_MS) { pushNow() }
        // wysyłka, która zaczęła się wcześniej i nadal trzyma zamek, też nie wstrzyma wylogowania
        scheduler.abort()
        scheduler.exclusive {
            googleSignIn.signOut()
            onSignedOut()
        }
        runBootstrap()
    }

    // sprawdzanie chmury po powrocie do aplikacji: dopiero po pierwszym bootstrapie, nie częściej niż co chwilę
    @Volatile private var bootstrapped = false
    private val lastCloudCheckAt = AtomicLong(Long.MIN_VALUE / 2)

    private val _migrationRequired = MutableStateFlow(false)
    val migrationRequired: StateFlow<Boolean> = _migrationRequired.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> get() = syncEngine.status

    /** Wołane raz, przy starcie aplikacji. */
    fun start() {
        scope.launch {
            runBootstrap()
            deckRepository.purgeExpiredArchives()
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
        // koniec sesji nauki (ukończona albo zamknięta) domyka zaległe oceny
        scope.launch {
            studySessionHolder.session
                .flatMapLatest { active ->
                    active?.engine?.state?.map { it.status == SessionStatus.FINISHED }
                        ?: flowOf(true)
                }
                .combine(reviewWrites.state) { sessionOver, writes ->
                    sessionOver && (writes.pending == 0 || writes.failed)
                }
                .distinctUntilChanged()
                .collect { sessionOver ->
                    // najpierw zapis ostatnich ocen w bazie, potem wysyłka; nieudane oceny
                    // zostają w kolejce i wyślą się po ponowieniu
                    if (sessionOver) {
                        reviewWrites.awaitIdle()
                        pushNow()
                    }
                }
        }
    }

    /** Start aplikacji i udane logowanie. */
    suspend fun runBootstrap() {
        when (scheduler.exclusive { bootstrapLoader.bootstrap() }) {
            BootstrapResult.Merged, BootstrapResult.SeededFreshAccount ->
                if (authRepository.currentUid() != null) scheduler.enqueueImmediate()

            BootstrapResult.LocalOnly -> Unit

            BootstrapResult.MigrationRequired -> _migrationRequired.value = true
        }
        bootstrapped = true
    }

    suspend fun resolveMigration(option: MigrationOption) {
        scope.async {
            accountChanges.withLock {
                if (option == MigrationOption.CANCEL) {
                    signOutAccount()
                } else {
                    scheduler.exclusive { migrationController.execute(option) }
                    _migrationRequired.value = false
                    pushNow()
                }
            }
        }.await()
    }

    private fun onSignedOut() {
        scheduler.cancel()
        syncEngine.clearBaseline()
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
    fun onAppForeground() {
        scope.launch { deckRepository.purgeExpiredArchives() }
        scope.launch { refreshIfCloudChanged() }
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
        if (now - last < FOREGROUND_CLOUD_CHECK_INTERVAL_MS || !lastCloudCheckAt.compareAndSet(last, now)) return
        val merged = scheduler.exclusive {
            namespaceManager.current() == uid && !syncEngine.hasFreshBaseline(uid) && reconcileWithCloud(uid)
        }
        // scalony stan może zawierać zmiany zrobione offline — wysyłka różnic (pusta, gdy ich nie ma)
        if (merged) scheduler.enqueueImmediate()
    }

    private suspend fun flushIfSignedIn() {
        if (namespaceManager.current() != NamespaceManager.GUEST) scheduler.flush()
    }

    private suspend fun push(): Boolean {
        val uid = namespaceManager.current()
        if (uid == NamespaceManager.GUEST || _migrationRequired.value) return true
        // ktoś zmienił chmurę albo start był offline: najpierw scal, inaczej wysłalibyśmy nieaktualny stan
        if (!syncEngine.hasFreshBaseline(uid) && !reconcileWithCloud(uid)) return false
        return syncEngine.save(uid, localStore.snapshot())
    }

    /** Zwraca false, gdy chmura jest nieosiągalna (scheduler ponowi przy następnej okazji). */
    private suspend fun reconcileWithCloud(uid: String): Boolean = when (val cloud = syncEngine.load(uid)) {
        is CloudLoad.Loaded -> {
            localStore.mergeWithCloud(cloud.data)
            true
        }

        CloudLoad.Empty -> true

        CloudLoad.Failed -> false
    }
}
