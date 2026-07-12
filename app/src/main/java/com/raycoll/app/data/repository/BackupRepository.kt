package com.raycoll.app.data.repository

import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.normalize.normalizeStoreData
import com.raycoll.app.domain.normalize.validateBackupJson
import com.raycoll.app.domain.seed.SeedData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val deckRepository: DeckRepository,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun exportJson(): String {
        val snapshot = deckRepository.getSnapshot()
        return json.encodeToString(snapshot)
    }

    suspend fun importJson(jsonText: String) {
        // walidacja PRZED dekodowaniem i zapisem — błędny plik zostawia stan nietknięty
        validateBackupJson(jsonText)
        val snapshot = json.decodeFromString<StoreData>(jsonText)
        val normalized = normalizeStoreData(snapshot)
        deckRepository.replaceAll(normalized)
    }

    suspend fun factoryReset() {
        deckRepository.replaceAll(SeedData.createSeedData())
    }
}
