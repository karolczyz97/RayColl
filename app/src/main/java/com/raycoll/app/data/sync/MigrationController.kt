package com.raycoll.app.data.sync

import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationController @Inject constructor(
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository,
    private val namespaceManager: NamespaceManager,
) {
    enum class MigrationOption { MIGRATE, FRESH, CANCEL }

    suspend fun executeMigration(option: MigrationOption) {
        when (option) {
            MigrationOption.MIGRATE -> {
                // Namespace jest już przełączony na uid (bootstrap zrobił to przed
                // dialogiem), więc dane gościa czytamy JAWNIE z namespace "" —
                // getSnapshot() zwróciłby pusty snapshot świeżego konta.
                // Kopiujemy (dane gościa zostają lokalnie nietknięte — plan T10).
                val guestData = deckRepository.getSnapshotFor("")
                namespaceManager.setNamespace(authRepository.currentUid())
                deckRepository.replaceAll(guestData)
            }
            MigrationOption.FRESH -> {
                namespaceManager.setNamespace(authRepository.currentUid())
                deckRepository.replaceAll(com.raycoll.app.domain.seed.SeedData.createSeedData())
            }
            MigrationOption.CANCEL -> {
                authRepository.signOut()
                namespaceManager.resetToGuest()
            }
        }
    }
}
