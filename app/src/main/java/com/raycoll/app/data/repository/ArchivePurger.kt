package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchivePurger @Inject constructor(
    private val deckRepository: DeckRepository,
    private val clock: Clock,
) {
    suspend fun purge(): Boolean = deckRepository.purgeExpiredArchives()
}
