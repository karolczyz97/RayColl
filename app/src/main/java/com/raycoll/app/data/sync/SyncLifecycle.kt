package com.raycoll.app.data.sync

import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spina cykl życia synchronizacji: bootstrap przy starcie apki i po zalogowaniu,
 * dialog migracji danych gościa, oraz debounced push lokalnych mutacji do Firestore.
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
) {
    private var scope: CoroutineScope? = null
    private var pushJob: Job? = null
    private var suppressPush = false

    private val _migrationRequired = MutableStateFlow(false)
    val migrationRequired: StateFlow<Boolean> = _migrationRequired.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> get() = syncEngine.status

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { runBootstrap() }
        scope.launch {
            deckRepository.mutations.collect {
                if (!suppressPush) schedulePush()
            }
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
        if (authRepository.isSignedIn()) schedulePush()
    }

    suspend fun resolveMigration(option: MigrationController.MigrationOption) {
        suppressPush = true
        try {
            migrationController.executeMigration(option)
        } finally {
            suppressPush = false
        }
        _migrationRequired.value = false
        if (option != MigrationController.MigrationOption.CANCEL && authRepository.isSignedIn()) {
            pushNow()
        }
    }

    fun onSignedOut() {
        pushJob?.cancel()
        syncEngine.clearCache()
        namespaceManager.resetToGuest()
        _migrationRequired.value = false
    }

    fun schedulePush() {
        val s = scope ?: return
        if (!authRepository.isSignedIn()) return
        pushJob?.cancel()
        pushJob = s.launch {
            delay(StudyConstants.PERSISTENCE_DEBOUNCE_MS)
            push()
        }
    }

    suspend fun pushNow() {
        pushJob?.cancel()
        push()
    }

    private suspend fun push() {
        val uid = authRepository.currentUid()
        if (uid.isEmpty()) return
        val snapshot: StoreData = deckRepository.getSnapshot()
        syncEngine.saveUserData(uid, snapshot)
    }
}
