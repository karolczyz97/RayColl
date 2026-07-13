package com.raycoll.app.data.sync

import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.merge.getGuestHasData
import com.raycoll.app.domain.merge.shouldTriggerMigration
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.seed.SeedData
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootstrapLoader @Inject constructor(
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository,
    private val namespaceManager: NamespaceManager,
    private val syncEngine: SyncEngine,
) {
    suspend fun bootstrap(): BootstrapResult {
        val uid = authRepository.currentUid()
        return if (uid.isEmpty()) {
            namespaceManager.resetToGuest()
            deckRepository.seedIfEmpty()
            BootstrapResult.LocalOnly
        } else {
            namespaceManager.setNamespace(uid)
            val localData = deckRepository.getSnapshot()
            try {
                val cloudData = withTimeout(8000) { syncEngine.loadCloudData(uid) }
                if (cloudData != null) {
                    val merged = syncEngine.bootstrap(localData, cloudData)
                    BootstrapResult.Merged(merged)
                } else {
                    // Cloud returned null = new account with no cloud data
                    val guestData = deckRepository.getSnapshotFor("")
                    val hasUserLocalCache = localData.groups.isNotEmpty() || localData.studyModes.isNotEmpty()
                    val guestHasData = getGuestHasData(guestData.groups, guestData.activityHeatmap, guestData.studyModes)
                    if (shouldTriggerMigration(hasUserLocalCache, guestHasData)) {
                        BootstrapResult.MigrationRequired(guestData)
                    } else {
                        deckRepository.seedIfEmpty()
                        BootstrapResult.LocalOnly
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Cloud timeout / błąd — pokaż dane lokalne (seed dokłada DashboardViewModel).
                BootstrapResult.LocalOnly
            } catch (e: Exception) {
                BootstrapResult.LocalOnly
            }
        }
    }
}

sealed class BootstrapResult {
    data object LocalOnly : BootstrapResult()
    data class Merged(val data: StoreData) : BootstrapResult()
    data class MigrationRequired(val guestData: StoreData) : BootstrapResult()
}
