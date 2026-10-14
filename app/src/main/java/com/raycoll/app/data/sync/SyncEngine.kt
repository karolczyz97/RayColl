package com.raycoll.app.data.sync

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.normalize.normalizeStoreData
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

enum class SyncStatus { IDLE, SYNCING, ERROR }

/** Wynik odczytu danych konta z chmury. */
sealed interface CloudLoad {
    data class Loaded(val data: StoreData) : CloudLoad

    /** Konto nie ma jeszcze danych w chmurze. */
    data object Empty : CloudLoad

    /** Chmura nieosiągalna (brak sieci, uprawnień) — stan nieznany. */
    data object Failed : CloudLoad
}

private const val TAG = "SyncEngine"

/** Limit operacji w jednym batchu Firestore (twardy limit backendu to 500). */
private const val MAX_BATCH_OPS = 400

private const val FIELD_UPDATED_AT = "updatedAt"

/**
 * Odczyt i zapis danych konta w Firestore. Zapis wysyła tylko różnice względem
 * ostatniego znanego stanu chmury ("bazy"), więc wymaga aktualnej bazy —
 * sprawdza to [hasFreshBaseline]. Odczyty idą zawsze z serwera, nie z cache offline.
 */
@Singleton
class SyncEngine @Inject constructor(private val firestore: FirebaseFirestore) : CloudSync {
    private val _status = MutableStateFlow(SyncStatus.IDLE)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Stan chmury po ostatnim udanym odczycie/zapisie i znacznik, po którym poznamy cudzy zapis. */
    private data class Baseline(val uid: String, val data: StoreData, val rootUpdatedAt: Timestamp?)

    private val mutex = Mutex()
    private var baseline: Baseline? = null

    override fun clearBaseline() {
        baseline = null
        _status.value = SyncStatus.IDLE
    }

    override suspend fun load(uid: String): CloudLoad = mutex.withLock {
        reportingStatus(_status, onError = CloudLoad.Failed) {
            val rootSnap = userRoot(uid).get(Source.SERVER).awaitCloud()
            val data = if (rootSnap.exists()) fetchData(uid) else StoreData()
            baseline = Baseline(uid, data, rootSnap.getTimestamp(FIELD_UPDATED_AT))
            if (rootSnap.exists()) CloudLoad.Loaded(data) else CloudLoad.Empty
        }
    }

    /**
     * Czy znana baza nadal odpowiada chmurze — inne urządzenie
     * nie zapisał niczego od naszego ostatniego odczytu/zapisu. Jeden odczyt dokumentu.
     */
    override suspend fun hasFreshBaseline(uid: String): Boolean = mutex.withLock {
        val known = baseline?.takeIf { it.uid == uid } ?: return@withLock false
        try {
            userRoot(uid).get(Source.SERVER).awaitCloud().getTimestamp(FIELD_UPDATED_AT) == known.rootUpdatedAt
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot verify cloud state: ${e.message}")
            false
        }
    }

    /** Wysyła różnice względem bazy. Bez bazy dla [uid] (patrz [load]) niczego nie wysyła. */
    override suspend fun save(uid: String, data: StoreData): Boolean = mutex.withLock {
        val known = baseline?.takeIf { it.uid == uid } ?: return@withLock false
        reportingStatus(_status, onError = false) {
            val ops = SyncDiff.diffSave(known.data, data)
            if (ops.isNotEmpty()) commit(uid, ops)
            val rootUpdatedAt = userRoot(uid).get(Source.SERVER).awaitCloud().getTimestamp(FIELD_UPDATED_AT)
            baseline = Baseline(uid, data, rootUpdatedAt)
            true
        }
    }

    /** Uszkodzony pojedynczy dokument jest pomijany — nie przekreśla odczytu całego konta. */
    private suspend fun fetchData(uid: String): StoreData = coroutineScope {
        val root = userRoot(uid)
        val decks = async {
            root.collection(FirestoreSchema.PATH_DECKS).get(Source.SERVER).awaitCloud().documents
                .map { doc -> async { fetchDeck(doc.reference, doc.id, doc.data.orEmpty()) } }
                .awaitAll()
                .filterNotNull()
        }
        val modes = async {
            root.collection(FirestoreSchema.PATH_MODES).get(Source.SERVER).awaitCloud().documents.mapNotNull { doc ->
                parseOrSkip("study mode ${doc.id}") { FirestoreSchema.modeFromMap(doc.id, doc.data.orEmpty()) }
            }
        }
        val activity = async {
            root.collection(FirestoreSchema.PATH_ACTIVITY).get(Source.SERVER).awaitCloud().documents.mapNotNull { doc ->
                parseOrSkip("activity day ${doc.id}") {
                    doc.id to
                        FirestoreSchema.activityCountFromMap(doc.id, doc.data.orEmpty())
                }
            }.toMap()
        }
        normalizeStoreData(
            StoreData(groups = decks.await(), studyModes = modes.await(), activityHeatmap = activity.await())
        )
    }

    private suspend fun fetchDeck(
        deckRef: DocumentReference,
        deckId: String,
        data: Map<String, Any?>
    ): FlashcardGroup? {
        val cards = deckRef.collection(
            FirestoreSchema.PATH_CARDS
        ).get(Source.SERVER).awaitCloud().documents.mapNotNull { doc ->
            parseOrSkip("card ${doc.id} in deck $deckId") { FirestoreSchema.cardFromMap(doc.id, doc.data.orEmpty()) }
        }
        return parseOrSkip("deck $deckId") { FirestoreSchema.deckFromMap(deckId, data, cards) }
    }

    private inline fun <T> parseOrSkip(what: String, parse: () -> T): T? = try {
        parse()
    } catch (e: Exception) {
        Log.w(TAG, "Skipping corrupt Firestore $what: ${e.message}")
        null
    }

    private suspend fun commit(uid: String, ops: List<SyncOp>) {
        val root = userRoot(uid)
        uploadInBatches(
            writes = ops.map { op -> write(root, op) },
            batchSize = MAX_BATCH_OPS,
            commitBatch = { chunk ->
                val batch = firestore.batch()
                for ((ref, value) in chunk) {
                    if (value == null) batch.delete(ref) else batch.set(ref, value)
                }
                batch.commit().await()
            },
            // po znaczniku inne urządzenia poznają zmianę; merge: tylko to pole, reszta dokumentu konta zostaje
            publishUpdatedAt = {
                root.set(mapOf(FIELD_UPDATED_AT to FieldValue.serverTimestamp()), SetOptions.merge()).await()
            }
        )
    }

    /** Dokument i jego nowa treść; null = usunięcie. */
    private fun write(root: DocumentReference, op: SyncOp): Pair<DocumentReference, Map<String, Any>?> = when (op) {
        is SyncOp.PutDeck -> deckRef(root, op.deck.id) to FirestoreSchema.deckToMap(op.deck)

        is SyncOp.DeleteDeck -> deckRef(root, op.deckId) to null

        is SyncOp.PutCard -> cardRef(root, op.deckId, op.card.id) to FirestoreSchema.cardToMap(op.card)

        is SyncOp.DeleteCard -> cardRef(root, op.deckId, op.cardId) to null

        is SyncOp.PutMode -> modeRef(root, op.mode.id) to FirestoreSchema.modeToMap(op.mode)

        is SyncOp.DeleteMode -> modeRef(root, op.modeId) to null

        is SyncOp.PutActivity -> activityRef(root, op.dayKey) to
            FirestoreSchema.activityToMap(op.count) + (FIELD_UPDATED_AT to FieldValue.serverTimestamp())

        is SyncOp.DeleteActivity -> activityRef(root, op.dayKey) to null
    }

    private fun userRoot(uid: String): DocumentReference =
        firestore.collection(FirestoreSchema.PATH_USERS).document(uid)

    private fun deckRef(root: DocumentReference, deckId: String) =
        root.collection(FirestoreSchema.PATH_DECKS).document(deckId)

    private fun cardRef(root: DocumentReference, deckId: String, cardId: String) =
        deckRef(root, deckId).collection(FirestoreSchema.PATH_CARDS).document(cardId)

    private fun modeRef(root: DocumentReference, modeId: String) =
        root.collection(FirestoreSchema.PATH_MODES).document(modeId)

    private fun activityRef(root: DocumentReference, dayKey: String) =
        root.collection(FirestoreSchema.PATH_ACTIVITY).document(dayKey)
}

/**
 * Wysyła [writes] partiami po [batchSize] i dopiero po ostatniej udanej partii publikuje
 * znacznik aktualizacji konta. Partie to osobne commity — razem NIE są transakcją, więc czytelnik
 * w trakcie wysyłki może zobaczyć część zmian; znacznik mówi tylko, że wysyłka się zakończyła.
 * Błąd partii przerywa wysyłkę bez publikacji znacznika; ponowienie wysyła wszystko od nowa
 * (zapisy „set”/„delete” całych dokumentów są idempotentne).
 */
internal suspend fun <W> uploadInBatches(
    writes: List<W>,
    batchSize: Int,
    commitBatch: suspend (List<W>) -> Unit,
    publishUpdatedAt: suspend () -> Unit
) {
    for (chunk in writes.chunked(batchSize)) cloudOperation { commitBatch(chunk) }
    cloudOperation { publishUpdatedAt() }
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
        Log.w(TAG, "Cloud sync failed: ${e.message}")
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

private suspend fun <T> Task<T>.awaitCloud(): T = cloudOperation { await() }
