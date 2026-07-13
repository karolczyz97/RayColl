package com.raycoll.app.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchivePurger @Inject constructor(
    private val deckRepository: DeckRepository,
) {
    suspend fun purge(): Boolean = deckRepository.purgeExpiredArchives()
}
