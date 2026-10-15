package com.raycoll.app.data.sync

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.normalize.normalizeStoreData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

enum class SyncStatus { IDLE, SYNCING, ERROR }

/** Wynik odczytu danych konta z chmury. */
sealed interface CloudLoad {
    data class Loaded(val data: StoreData, val positionsComplete: Boolean = true) : CloudLoad

    /** Konto nie ma jeszcze danych w chmurze. */
    data object Empty : CloudLoad

    /** Chmura nieosiągalna (brak sieci, uprawnień) — stan nieznany. */
    data object Failed : CloudLoad
}

private const val TAG = "SyncEngine"

/** Limit operacji w jednym batchu Firestore (twardy limit backendu to 500). */
private const val MAX_BATCH_OPS = 400

/**
 * Odczyt i zapis danych konta w Firestore. Zapis wysyła tylko różnice względem
 * ostatniego znanego stanu chmury ("bazy"), więc wymaga aktualnej bazy —
 * sprawdza to [hasFreshBaseline]. Odczyty idą zawsze z serwera, nie z cache offline.
 */
@Singleton
class SyncEngine @Inject constructor(private val firestore: FirebaseFirestore) : CloudSync {
    private val _status = MutableStateFlow(SyncStatus.IDLE)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /**
     * Stan chmury po ostatnim udanym odczycie/zapisie i znacznik, po którym poznamy cudzy zapis.
     * [stalePositions]: dokumenty, których zapisana pozycja różni się od miejsca na liście (patrz [SyncDiff]).
     */
    private data class Baseline(
        val uid: String,
        val data: StoreData,
        val revision: CloudRevision,
        val stalePositions: Set<String> = emptySet()
    )

    private val mutex = Mutex()

    @Volatile private var baseline: Baseline? = null

    override fun clearBaseline() {
        baseline = null
        _status.value = SyncStatus.IDLE
    }

    override fun reportFailure() {
        _status.value = SyncStatus.ERROR
    }

    override suspend fun load(uid: String): CloudLoad = mutex.withLock {
        reportingStatus(_status, onError = CloudLoad.Failed) {
            val root = userRoot(uid)
            // Odczyt kilku kolekcji nie jest jednym snapshotem. Nie przyjmujemy bazy,
            // jeśli w czasie odczytu inne urządzenie opublikowało zmianę.
            repeat(3) {
                val before = root.get(Source.SERVER).awaitCloud()
                val fetched = fetchData(uid)
                val after = root.get(Source.SERVER).awaitCloud()
                if (cloudRevision(before.data.orEmpty()) != cloudRevision(after.data.orEmpty())) return@repeat
                val (replacementAt, replacementId) = FirestoreSchema.replacementFromMap(after.data.orEmpty())
                val data = fetched.data.copy(replacementAt = replacementAt, replacementId = replacementId)
                val visible = visibleCloudGeneration(data, fetched.generations)
                val visibleKeys = positionsOfVisible(visible)
                baseline = Baseline(
                    uid,
                    data,
                    cloudRevision(after.data.orEmpty()),
                    stalePositionsOf(data, fetched.positions) + staleGenerationKeys(data, fetched.generations)
                )
                return@reportingStatus if (after.exists() || data.groups.isNotEmpty() ||
                    data.studyModes.isNotEmpty() || data.activityHeatmap.isNotEmpty()
                ) {
                    CloudLoad.Loaded(
                        visible,
                        fetched.positions.filterKeys { key ->
                            key in visibleKeys
                        }.values.all { it != null }
                    )
                } else {
                    CloudLoad.Empty
                }
            }
            throw CloudChangedException()
        }
    }

    /**
     * Czy znana baza nadal odpowiada chmurze — inne urządzenie
     * nie zapisało niczego od naszego ostatniego odczytu/zapisu. Jeden odczyt dokumentu.
     */
    override suspend fun hasFreshBaseline(uid: String): Boolean = mutex.withLock {
        val known = baseline?.takeIf { it.uid == uid } ?: return@withLock false
        try {
            cloudRevision(userRoot(uid).get(Source.SERVER).awaitCloud().data.orEmpty()) == known.revision
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot verify cloud state", e)
            false
        }
    }

    /** Wysyła różnice względem bazy. Bez bazy dla [uid] (patrz [load]) niczego nie wysyła. */
    override suspend fun save(uid: String, data: StoreData): Boolean = mutex.withLock {
        val known = baseline?.takeIf { it.uid == uid } ?: return@withLock false
        reportingStatus(_status, onError = false) {
            val ops = SyncDiff.diffSave(known.data, data, known.stalePositions)
            // Nie przyjmujemy nowego znacznika cudzej zmiany bez jej odczytania i scalenia.
            if (!cloudCommitRequired(known.data, data, ops)) return@reportingStatus true
            val revision = commit(uid, ops, data, known)
            // clearBaseline po wylogowaniu nie może zostać cofnięte przez kończący się zapis.
            if (baseline === known) baseline = Baseline(uid, data, revision)
            true
        }
    }

    /**
     * Uszkodzony pojedynczy dokument jest pomijany — nie przekreśla odczytu całego konta.
     * Talie i karty wracają w kolejności z pola `position` (sama chmura sortuje po id dokumentu).
     */
    private suspend fun fetchData(uid: String): FetchedData = coroutineScope {
        val root = userRoot(uid)
        val generations = ConcurrentHashMap<String, Pair<Long, String>>()
        val decks = async {
            val docs = root.collection(FirestoreSchema.PATH_DECKS).serverDocs()
            docs.map { doc -> async { fetchDeck(doc.reference, doc.id, doc.data.orEmpty(), generations) } }
                .awaitAll()
                .filterNotNull()
                .sortedByPosition()
        }
        val modes = async {
            root.collection(FirestoreSchema.PATH_MODES).serverDocs().mapNotNull { doc ->
                parseOrSkip("study mode ${doc.id}") {
                    FirestoreSchema.modeFromMap(doc.id, doc.data.orEmpty()).also {
                        generations[modeKey(doc.id)] = FirestoreSchema.replacementFromMap(doc.data.orEmpty())
                    }
                }
            }
        }
        val activity = async {
            root.collection(FirestoreSchema.PATH_ACTIVITY).serverDocs().mapNotNull { doc ->
                parseOrSkip("activity day ${doc.id}") {
                    doc.id to
                        FirestoreSchema.activityCountFromMap(doc.id, doc.data.orEmpty()).also {
                            generations[activityKey(doc.id)] = FirestoreSchema.replacementFromMap(doc.data.orEmpty())
                        }
                }
            }.toMap()
        }
        val orderedDecks = decks.await()
        FetchedData(
            data = normalizeStoreData(
                StoreData(
                    groups = orderedDecks.map { deck -> deck.value.copy(cards = deck.cards.map { it.value }) },
                    studyModes = modes.await(),
                    activityHeatmap = activity.await()
                )
            ),
            positions = positionsOf(orderedDecks),
            generations = generations
        )
    }

    private suspend fun CollectionReference.serverDocs() = get(Source.SERVER).awaitCloud().documents

    private suspend fun fetchDeck(
        deckRef: DocumentReference,
        deckId: String,
        data: Map<String, Any?>,
        generations: MutableMap<String, Pair<Long, String>>
    ): FetchedDeck? {
        val cards = deckRef.collection(
            FirestoreSchema.PATH_CARDS
        ).serverDocs().mapNotNull { doc ->
            val cardData = doc.data.orEmpty()
            parseOrSkip("card ${doc.id} in deck $deckId") {
                val card = FirestoreSchema.cardFromMap(doc.id, cardData)
                generations[cardKey(deckId, doc.id)] = FirestoreSchema.replacementFromMap(cardData)
                Positioned(doc.id, card, FirestoreSchema.positionFromMap(cardData))
            }
        }.sortedByPosition()
        return parseOrSkip("deck $deckId") {
            val deck = FirestoreSchema.deckFromMap(deckId, data)
            generations[deckKey(deckId)] = FirestoreSchema.replacementFromMap(data)
            FetchedDeck(deckId, deck, FirestoreSchema.positionFromMap(data), cards)
        }
    }

    private inline fun <T> parseOrSkip(what: String, parse: () -> T): T? = try {
        parse()
    } catch (e: Exception) {
        Log.w(TAG, "Skipping corrupt Firestore $what", e)
        null
    }

    private suspend fun commit(uid: String, ops: List<SyncOp>, data: StoreData, known: Baseline): CloudRevision {
        val root = userRoot(uid)
        var expected = known.revision
        var expectedData = known.data
        // Każda porcja i jej znacznik trafiają do jednej transakcji. Cudzy zapis
        // pomiędzy sprawdzeniem bazy a commitem wymusza odczyt i scalenie.
        commitInChunks(ops, MAX_BATCH_OPS) { chunk ->
            val next = CloudRevision(Timestamp.now(), UUID.randomUUID().toString())
            val expectedRevision = expected
            val expectedGeneration = expectedData
            firestore.runTransaction { transaction ->
                val current = transaction.get(root).data.orEmpty()
                requireCloudBaseline(current, expectedRevision, expectedGeneration)
                for (op in chunk) {
                    val (ref, value) = write(root, op)
                    if (value == null) transaction.delete(ref) else transaction.set(ref, value + generationFields(data))
                }
                transaction.set(
                    root,
                    mapOf(
                        FirestoreSchema.FIELD_UPDATED_AT to next.updatedAt,
                        FIELD_SYNC_TOKEN to next.token,
                        FirestoreSchema.FIELD_REPLACEMENT_AT to data.replacementAt,
                        FirestoreSchema.FIELD_REPLACEMENT_ID to data.replacementId
                    ),
                    SetOptions.merge()
                )
                Unit
            }.await()
            expected = next
            expectedData = data
        }
        return expected
    }

    /** Dokument i jego nowa treść; null = usunięcie. */
    private fun write(root: DocumentReference, op: SyncOp): Pair<DocumentReference, Map<String, Any>?> = when (op) {
        is SyncOp.PutDeck -> deckRef(root, op.deck.id) to FirestoreSchema.deckToMap(op.deck, op.position)

        is SyncOp.DeleteDeck -> deckRef(root, op.deckId) to null

        is SyncOp.PutCard -> cardRef(root, op.deckId, op.card.id) to FirestoreSchema.cardToMap(op.card, op.position)

        is SyncOp.DeleteCard -> cardRef(root, op.deckId, op.cardId) to null

        is SyncOp.PutMode -> modeRef(root, op.mode.id) to FirestoreSchema.modeToMap(op.mode)

        is SyncOp.DeleteMode -> modeRef(root, op.modeId) to null

        // marker dokumentu aktywności ustawia serwer
        is SyncOp.PutActivity -> activityRef(root, op.dayKey) to
            FirestoreSchema.activityToMap(op.count) + (FirestoreSchema.FIELD_UPDATED_AT to FieldValue.serverTimestamp())

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
