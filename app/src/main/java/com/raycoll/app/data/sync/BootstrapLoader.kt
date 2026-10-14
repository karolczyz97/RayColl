package com.raycoll.app.data.sync

import com.raycoll.app.data.repository.LocalStore
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.merge.getGuestHasData
import com.raycoll.app.domain.merge.shouldTriggerMigration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

sealed interface BootstrapResult {
    /** Pracujemy na danych lokalnych (gość, konto bez danych w chmurze albo chmura nieosiągalna). */
    data object LocalOnly : BootstrapResult

    /** Dane z chmury scalone z lokalnymi — trzeba odesłać wynik. */
    data object Merged : BootstrapResult

    /**
     * Nowe konto z pustą chmurą dostało dane startowe (albo ma je z poprzedniej, niewysłanej
     * próby) — to dane konta, więc trzeba je wysłać, żeby drugie urządzenie zobaczyło to samo.
     */
    data object SeededFreshAccount : BootstrapResult

    /** Nowe konto, a gość ma dane — użytkownik decyduje, co z nimi zrobić. */
    data object MigrationRequired : BootstrapResult
}

private const val CLOUD_LOAD_TIMEOUT_MS = 8_000L

/** Start aplikacji i logowanie: wybór przestrzeni danych i pierwsze scalenie z chmurą. */
@Singleton
class BootstrapLoader @Inject constructor(
    private val authRepository: CurrentUser,
    private val localStore: LocalStore,
    private val namespaceManager: NamespaceManager,
    private val syncEngine: CloudSync
) {
    suspend fun bootstrap(): BootstrapResult {
        val uid = authRepository.currentUid()
        if (uid == null) {
            namespaceManager.switchToGuest()
            localStore.seedIfEmpty()
            return BootstrapResult.LocalOnly
        }
        namespaceManager.switchTo(uid)
        return when (
            val cloud =
                withTimeoutOrNull(CLOUD_LOAD_TIMEOUT_MS) { syncEngine.load(uid) } ?: CloudLoad.Failed
        ) {
            is CloudLoad.Loaded -> {
                localStore.mergeWithCloud(cloud.data)
                BootstrapResult.Merged
            }

            // bez seedowania: chmura może mieć dane, a seed po scaleniu dałby duplikaty;
            // pierwszy zapis i tak najpierw scali stan z chmurą
            CloudLoad.Failed -> BootstrapResult.LocalOnly

            CloudLoad.Empty -> bootstrapFreshAccount()
        }.also { localStore.refreshUntouchedBuiltInModes() }
    }

    private suspend fun bootstrapFreshAccount(): BootstrapResult {
        val local = localStore.snapshot()
        val guest = localStore.snapshotOf(NamespaceManager.GUEST)
        val hasUserLocalCache = local.groups.isNotEmpty() || local.studyModes.isNotEmpty()
        val guestHasData = getGuestHasData(guest.groups, guest.activityHeatmap, guest.studyModes)
        if (shouldTriggerMigration(hasUserLocalCache, guestHasData)) return BootstrapResult.MigrationRequired
        // seed tylko w pustej przestrzeni konta — kolejne starty (np. po nieudanej wysyłce) nie dublują talii
        localStore.seedIfEmpty()
        return BootstrapResult.SeededFreshAccount
    }
}
