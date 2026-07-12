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
                // Copy guest data to account namespace
                val guestData = deckRepository.getSnapshot()
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
