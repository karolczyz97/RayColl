package com.raycoll.app.data.sync

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.merge.mergeUserData
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.normalize.normalizeStoreData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncStatus { IDLE, SAVING, SYNCING, ERROR }

private const val TAG = "SyncEngine"

/** Limit operacji na batch Firestore (twardy limit backendu to 500). */
private const val MAX_BATCH_OPS = 400

@Singleton
class SyncEngine @Inject constructor(
    private val deckRepository: DeckRepository,
    private val firestore: FirebaseFirestore,
) {
    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    // baza do diff-save'ów: ostatni znany PEŁNY stan chmury dla danego uid;
    // aktualizowana wyłącznie po pełnym sukcesie loadu/save'u
    private var cachedUid: String? = null
    private var cloudCache: StoreData? = null
    private var lastSyncError: String? = null

    fun clearCache() {
        cachedUid = null
        cloudCache = null
        lastSyncError = null
    }

    /**
     * Pełny load stanu chmury. Uszkodzony pojedynczy dokument (karta/talia/tryb/
     * dzień) jest pomijany z warningiem — nie wywala całego loadu (parytet z
     * loadUserData w firestorePersistence.ts). Zwraca null dla konta bez danych.
     */
    suspend fun loadCloudData(uid: String): StoreData? {
        return try {
            val cloudData = fetchCloudSnapshot(uid)
            // udany odczyt (także "konto bez danych" = null) czyści błąd —
            // BootstrapLoader odróżnia po lastSyncError świeże konto od błędu sieci
            lastSyncError = null
            if (cloudData != null) {
                cachedUid = uid
                cloudCache = cloudData
                _status.value = SyncStatus.IDLE
            }
            cloudData
        } catch (e: CancellationException) {
            throw e // timeout/anulowanie obsługuje BootstrapLoader
        } catch (e: Exception) {
            lastSyncError = e.message
            _status.value = SyncStatus.ERROR
            null
        }
    }

    /** Surowy odczyt snapshotu chmury — bez dotykania cache'u/statusu; rzuca przy błędzie sieci. */
    private suspend fun fetchCloudSnapshot(uid: String): StoreData? {
        val rootRef = userRoot(uid)
        val rootSnap = rootRef.get().await()
        if (!rootSnap.exists()) return null

        val groups = mutableListOf<FlashcardGroup>()
        val decksSnap = rootRef.collection(FirestoreSchema.PATH_DECKS).get().await()
        for (deckDoc in decksSnap) {
            val cards = mutableListOf<Flashcard>()
            val cardsSnap = deckDoc.reference.collection(FirestoreSchema.PATH_CARDS).get().await()
            for (cardDoc in cardsSnap) {
                try {
                    cards.add(FirestoreSchema.cardFromMap(cardDoc.id, cardDoc.data))
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt Firestore card ${cardDoc.id} in deck ${deckDoc.id}: ${e.message}")
                }
            }
            try {
                groups.add(FirestoreSchema.deckFromMap(deckDoc.id, deckDoc.data, cards))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt Firestore deck ${deckDoc.id}: ${e.message}")
            }
        }

        val modes = mutableListOf<StudyMode>()
        val modesSnap = rootRef.collection(FirestoreSchema.PATH_MODES).get().await()
        for (modeDoc in modesSnap) {
            try {
                modes.add(FirestoreSchema.modeFromMap(modeDoc.id, modeDoc.data))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt Firestore study mode ${modeDoc.id}: ${e.message}")
            }
        }

        val heatmap = mutableMapOf<String, Int>()
        val activitySnap = rootRef.collection(FirestoreSchema.PATH_ACTIVITY).get().await()
        for (dayDoc in activitySnap) {
            try {
                heatmap[dayDoc.id] = FirestoreSchema.activityCountFromMap(dayDoc.id, dayDoc.data)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt Firestore activity day ${dayDoc.id}: ${e.message}")
            }
        }

        return normalizeStoreData(
            StoreData(groups = groups, studyModes = modes, activityHeatmap = heatmap)
        )
    }

    /**
     * Zapis stanu do chmury. Z cachem → diff per dokument; bez cache'u →
     * pełny zapis z czyszczeniem osieroconych dokumentów (świeży load jako baza).
     * Operacje idą w batchach po [MAX_BATCH_OPS] (limit Firestore 500/batch).
     * Cache aktualizowany TYLKO po pełnym sukcesie — nieudany zapis zostaje
     * do ponowienia przy następnym flushu. Zwraca true przy sukcesie.
     */
    suspend fun saveUserData(uid: String, data: StoreData): Boolean {
        _status.value = SyncStatus.SYNCING
        return try {
            val previous = if (cachedUid == uid) cloudCache else null
            val ops = if (previous != null) {
                SyncDiff.diffSave(previous, data)
            } else {
                // bez cache'u: świeży load chmury jako baza czyszczenia osieroconych;
                // błąd loadu przerywa zapis (żeby nie pominąć kasowań po cichu)
                val existing = fetchCloudSnapshot(uid) ?: StoreData()
                SyncDiff.fullSave(existing, data)
            }
            executeOps(uid, ops)
            cachedUid = uid
            cloudCache = data
            lastSyncError = null
            _status.value = SyncStatus.IDLE
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastSyncError = e.message
            _status.value = SyncStatus.ERROR
            false
        }
    }

    private suspend fun executeOps(uid: String, ops: List<SyncOp>) {
        val rootRef = userRoot(uid)
        // znacznik root zawsze — utrzymuje `exists()` i updatedAt dla bootstrapu
        val writes: List<Pair<DocumentReference, Any?>> = buildList {
            add(rootRef to mapOf("updatedAt" to FieldValue.serverTimestamp()))
            for (op in ops) {
                when (op) {
                    is SyncOp.PutDeck -> add(deckRef(rootRef, op.deck.id) to FirestoreSchema.deckToMap(op.deck))
                    is SyncOp.DeleteDeck -> add(deckRef(rootRef, op.deckId) to null)
                    is SyncOp.PutCard -> add(cardRef(rootRef, op.deckId, op.card.id) to FirestoreSchema.cardToMap(op.card))
                    is SyncOp.DeleteCard -> add(cardRef(rootRef, op.deckId, op.cardId) to null)
                    is SyncOp.PutMode -> add(modeRef(rootRef, op.mode.id) to FirestoreSchema.modeToMap(op.mode))
                    is SyncOp.DeleteMode -> add(modeRef(rootRef, op.modeId) to null)
                    is SyncOp.PutActivity -> add(
                        activityRef(rootRef, op.dayKey) to
                            (FirestoreSchema.activityToMap(op.count) + mapOf("updatedAt" to FieldValue.serverTimestamp()))
                    )
                    is SyncOp.DeleteActivity -> add(activityRef(rootRef, op.dayKey) to null)
                }
            }
        }
        for (chunk in writes.chunked(MAX_BATCH_OPS)) {
            val batch = firestore.batch()
            for ((ref, value) in chunk) {
                if (value == null) batch.delete(ref) else batch.set(ref, value)
            }
            batch.commit().await()
        }
    }

    private fun userRoot(uid: String): DocumentReference =
        firestore.collection(FirestoreSchema.PATH_USERS).document(uid)

    private fun deckRef(root: DocumentReference, deckId: String): DocumentReference =
        root.collection(FirestoreSchema.PATH_DECKS).document(deckId)

    private fun cardRef(root: DocumentReference, deckId: String, cardId: String): DocumentReference =
        deckRef(root, deckId).collection(FirestoreSchema.PATH_CARDS).document(cardId)

    private fun modeRef(root: DocumentReference, modeId: String): DocumentReference =
        root.collection(FirestoreSchema.PATH_MODES).document(modeId)

    private fun activityRef(root: DocumentReference, dayKey: String): DocumentReference =
        root.collection(FirestoreSchema.PATH_ACTIVITY).document(dayKey)

    /** Merge lokalnego stanu ze stanem chmury i zapis wyniku do Room. */
    suspend fun bootstrap(localData: StoreData, cloudData: StoreData?): StoreData {
        return if (cloudData != null) {
            val merged = mergeUserData(localData, cloudData)
            deckRepository.replaceAll(merged)
            merged
        } else {
            localData
        }
    }

    fun lastSyncError(): String? = lastSyncError
}
