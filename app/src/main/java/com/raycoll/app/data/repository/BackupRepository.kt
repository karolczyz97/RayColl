package com.raycoll.app.data.repository

import android.net.Uri
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.normalize.BackupValidationException
import com.raycoll.app.domain.normalize.validateBackupJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Wynik operacji na pliku kopii zapasowej. */
enum class BackupResult {
    SUCCESS,

    /** Pliku nie dało się otworzyć, odczytać albo zapisać. */
    FILE_ERROR,

    /** Plik nie jest kopią zapasową RayColl (zły format, brak wymaganych danych). */
    INVALID_BACKUP
}

/** Kopia JSON zgodna z zapisanymi kopiami użytkowników i przywracanie stanu początkowego. */
@Singleton
class BackupRepository @Inject constructor(
    private val localStore: LocalStore,
    private val documentFiles: DocumentFiles,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun exportTo(uri: Uri): BackupResult {
        val snapshot = localStore.snapshot()
        val text = withContext(dispatcher) { json.encodeToString(StoreData.serializer(), snapshot) }
        return if (documentFiles.writeText(uri, text)) BackupResult.SUCCESS else BackupResult.FILE_ERROR
    }

    /** Wczytuje kopię i ZASTĘPUJE nią wszystkie dane; błędny plik zostawia dane nietknięte. */
    suspend fun importFrom(uri: Uri): BackupResult {
        val text = documentFiles.readText(uri) ?: return BackupResult.FILE_ERROR
        val data = try {
            withContext(dispatcher) {
                validateBackupJson(text)
                json.decodeFromString(StoreData.serializer(), text)
            }
        } catch (_: BackupValidationException) {
            return BackupResult.INVALID_BACKUP
        } catch (_: SerializationException) {
            return BackupResult.INVALID_BACKUP
        }
        localStore.replaceAll(data)
        return BackupResult.SUCCESS
    }

    /** Usuwa wszystkie dane aktywnego konta i wgrywa dane startowe. */
    suspend fun factoryReset() = localStore.replaceAll(localStore.seedData())
}
