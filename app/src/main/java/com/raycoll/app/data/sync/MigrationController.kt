package com.raycoll.app.data.sync

import com.raycoll.app.data.repository.LocalStore
import com.raycoll.app.data.repository.NamespaceManager
import javax.inject.Inject
import javax.inject.Singleton

enum class MigrationOption {
    /** Skopiuj dane gościa na konto (u gościa zostają). */
    MIGRATE,

    /** Zacznij konto od danych startowych. */
    FRESH,

    /** Zrezygnuj z logowania. */
    CANCEL
}

@Singleton
class MigrationController @Inject constructor(
    private val localStore: LocalStore,
    private val namespaceManager: NamespaceManager
) {
    /** Wykonuje wybór użytkownika; wysyłkę do chmury robi wołający. */
    suspend fun execute(option: MigrationOption) {
        when (option) {
            MigrationOption.MIGRATE -> localStore.replaceAll(
                localStore.snapshotOf(NamespaceManager.GUEST).copy(replacementAt = 0, replacementId = ""),
                notifySync = false
            )

            // nowe konto: stałe id seedów, jak przy pierwszym starcie z pustą chmurą
            MigrationOption.FRESH -> localStore.replaceAll(
                localStore.seedData(idScope = namespaceManager.current()),
                notifySync = false
            )

            MigrationOption.CANCEL -> Unit // wylogowanie należy do SyncLifecycle
        }
    }
}
