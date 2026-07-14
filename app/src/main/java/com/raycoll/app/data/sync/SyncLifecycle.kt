package com.raycoll.app.data.sync

import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.repository.CloudWriteMode
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.playback.StudySessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spina cykl życia synchronizacji: bootstrap przy starcie apki i po zalogowaniu,
 * dialog migracji danych gościa, oraz wypychanie lokalnych mutacji do Firestore
 * wg polityki SyncScheduler (debounce 1200 ms / próg 10 ocen study / immediate).
 * Dodatkowo flush na granicy sesji nauki i przy przejściu apki w tło.
 * Startowany raz z RayCollApplication.
 */
@Singleton
class SyncLifecycle @Inject constructor(
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository,
    private val namespaceManager: NamespaceManager,
    private val bootstrapLoader: BootstrapLoader,
    private val migrationController: MigrationController,
    private val syncEngine: SyncEngine,
    private val dataMutations: DataMutations,
    private val studySessionHolder: StudySessionHolder,
) {
    private var scope: CoroutineScope? = null
    private var scheduler: SyncScheduler? = null
    private var suppressPush = false

    private val _migrationRequired = MutableStateFlow(false)
    val migrationRequired: StateFlow<Boolean> = _migrationRequired.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> get() = syncEngine.status

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        this.scope = scope
        val sched = SyncScheduler(scope, push = ::push)
        scheduler = sched

        scope.launch { runBootstrap() }

        scope.launch {
            dataMutations.events.collect { mode ->
                if (suppressPush || !authRepository.isSignedIn()) return@collect
                when (mode) {
                    CloudWriteMode.DEBOUNCED -> sched.enqueueDebounced()
                    CloudWriteMode.STUDY -> sched.enqueueStudy()
                    CloudWriteMode.IMMEDIATE -> sched.enqueueImmediate()
                }
            }
        }

        // granica sesji study: koniec sesji (FINISHED) lub zwolnienie silnika
        // (wyjście z ekranu) domyka zaległe oceny — sesja 12 ocen = max 2 zapisy
        scope.launch {
            studySessionHolder.engineFlow
                .flatMapLatest { engine ->
                    engine?.state?.map { it.status == SessionStatus.FINISHED } ?: emptyFlow()
                }
                .distinctUntilChanged()
                .collect { finished -> if (finished) flushIfSignedIn() }
        }
        scope.launch {
            studySessionHolder.engineFlow
                .map { it != null }
                .distinctUntilChanged()
                .collect { active -> if (!active) flushIfSignedIn() }
        }
    }

    /** Wołane przy starcie apki i po udanym logowaniu. */
    suspend fun runBootstrap() {
        suppressPush = true
        try {
            when (val result = bootstrapLoader.bootstrap()) {
                is BootstrapResult.LocalOnly -> Unit
                is BootstrapResult.Merged -> Unit // merge zapisany do Room w SyncEngine.bootstrap
                is BootstrapResult.MigrationRequired -> {
                    _migrationRequired.value = true
                    return
                }
            }
        } finally {
            suppressPush = false
        }
        // po bootstrapie wyślij stan lokalny (obejmuje wynik merge)
        if (authRepository.isSignedIn()) scheduler?.enqueueImmediate()
    }

    suspend fun resolveMigration(option: MigrationController.MigrationOption) {
        suppressPush = true
        try {
            migrationController.executeMigration(option)
        } finally {
            suppressPush = false
        }
        _migrationRequired.value = false
        if (option == MigrationController.MigrationOption.CANCEL) {
            onSignedOut()
        } else if (authRepository.isSignedIn()) {
            pushNow()
        }
    }

    fun onSignedOut() {
        scheduler?.cancel()
        syncEngine.clearCache()
        namespaceManager.resetToGuest()
        _migrationRequired.value = false
    }

    /** Best-effort flush przed wylogowaniem — błąd chmury nie blokuje wylogowania. */
    suspend fun flushBeforeSignOut() {
        if (authRepository.isSignedIn()) {
            scheduler?.enqueueImmediate()
            scheduler?.flush()
        }
    }

    /** Flush przy przejściu apki w tło (ProcessLifecycle ON_STOP). */
    fun flushOnBackground() {
        val s = scope ?: return
        s.launch { flushIfSignedIn() }
    }

    suspend fun pushNow() {
        scheduler?.enqueueImmediate()
        scheduler?.flush()
    }

    private suspend fun flushIfSignedIn() {
        if (authRepository.isSignedIn()) scheduler?.flush()
    }

    private suspend fun push(): Boolean {
        val uid = authRepository.currentUid()
        if (uid.isEmpty()) return true
        val snapshot: StoreData = deckRepository.getSnapshot()
        return syncEngine.saveUserData(uid, snapshot)
    }
}
