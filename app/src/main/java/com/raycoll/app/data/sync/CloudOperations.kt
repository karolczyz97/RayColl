package com.raycoll.app.data.sync

import android.util.Log
import com.google.android.gms.tasks.Task
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Kolejne porcje transakcji. Każda porcja zapisuje własny znacznik razem z dokumentami;
 * błąd przerywa następne porcje, a ponowienie najpierw odczytuje i scala aktualną chmurę.
 * Pusta lista pozwala opublikować samą zmianę generacji. Łączny czas nie ma limitu,
 * każde pojedyncze żądanie ma swój timeout.
 */
internal suspend fun <W> commitInChunks(writes: List<W>, batchSize: Int, commitBatch: suspend (List<W>) -> Unit) {
    for (chunk in writes.chunked(batchSize).ifEmpty { listOf(emptyList()) }) {
        cloudOperation { commitBatch(chunk) }
    }
}

/**
 * Status synchronizacji wokół całej operacji. Celowo bez łącznego limitu czasu — ma go
 * każde żądanie z osobna ([cloudOperation]), więc długa wysyłka wielu partii się kończy.
 */
internal suspend fun <T> reportingStatus(status: MutableStateFlow<SyncStatus>, onError: T, block: suspend () -> T): T {
    status.value = SyncStatus.SYNCING
    return try {
        block().also { status.value = SyncStatus.IDLE }
    } catch (e: CancellationException) {
        status.value = SyncStatus.IDLE
        throw e
    } catch (e: Exception) {
        Log.w("SyncEngine", "Cloud sync failed", e)
        status.value = SyncStatus.ERROR
        onError
    }
}

/**
 * Limit jednego żądania do chmury (odczyt, partia, znacznik) — nie całej synchronizacji,
 * więc duży import w wielu partiach może trwać dłużej, a zawieszone żądanie i tak puszcza zamek.
 */
internal const val CLOUD_OPERATION_TIMEOUT_MS = 30_000L

/** Timeout jest błędem sieci do ponowienia; anulowanie właściciela nadal przerywa pracę. */
internal suspend fun <T> cloudOperation(block: suspend () -> T): T = try {
    withTimeout(CLOUD_OPERATION_TIMEOUT_MS) { block() }
} catch (e: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    throw IOException("Cloud operation timed out", e)
}

internal suspend fun <T> Task<T>.awaitCloud(): T = cloudOperation { await() }
