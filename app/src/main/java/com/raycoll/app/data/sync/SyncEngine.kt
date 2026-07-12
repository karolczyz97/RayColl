package com.raycoll.app.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.merge.mergeUserData
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.normalize.normalizeStoreData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncStatus { IDLE, SAVING, SYNCING, ERROR }

@Singleton
class SyncEngine @Inject constructor(
    private val deckRepository: DeckRepository,
    private val namespaceManager: NamespaceManager,
    private val firestore: FirebaseFirestore,
) {
    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var cloudCache: StoreData? = null
    private var lastSyncError: String? = null

    fun clearCache() { cloudCache = null; lastSyncError = null }

    suspend fun loadCloudData(uid: String): StoreData? {
        return try {
            val rootRef = firestore.collection(FirestoreSchema.PATH_USERS).document(uid)
            val rootSnap = rootRef.get().await()
            if (!rootSnap.exists()) return null

            val groups = mutableListOf<FlashcardGroup>()
            val modes = mutableListOf<StudyMode>()
            val heatmap = mutableMapOf<String, Int>()

            // Load decks
            val decksSnap = rootRef.collection(FirestoreSchema.PATH_DECKS).get().await()
            for (deckDoc in decksSnap) {
                val deckData = deckDoc.data
                val deckId = deckData["id"] as? String ?: continue
                val name = deckData["name"] as? String ?: continue
                val activeModeId = deckData["activeModeId"] as? String ?: "classic"
                val studyFilter = deckData["studyFilter"] as? String ?: "new+review"
                val cardOrder = deckData["cardOrder"] as? String ?: "sequential"
                @Suppress("UNCHECKED_CAST")
                val pageNames = (deckData["pageNames"] as? List<String>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val pageLanguages = (deckData["pageLanguages"] as? List<String>) ?: emptyList()
                val activePageCount = (deckData["activePageCount"] as? Number)?.toInt() ?: 2
                val updatedAt = (deckData["updatedAt"] as? Number)?.toLong() ?: 0
                val deletedAt = (deckData["deletedAt"] as? Number)?.toLong()
                val archivedAt = (deckData["archivedAt"] as? Number)?.toLong()

                val cards = mutableListOf<Flashcard>()
                val cardsSnap = deckDoc.reference.collection(FirestoreSchema.PATH_CARDS).get().await()
                for (cardDoc in cardsSnap) {
                    val cardData = cardDoc.data
                    val cardId = cardData["id"] as? String ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val pages = (cardData["pages"] as? List<String>) ?: emptyList()
                    val difficulty = (cardData["difficulty"] as? Number)?.toDouble() ?: 5.0
                    val stability = (cardData["stability"] as? Number)?.toDouble() ?: 0.0
                    val reps = (cardData["repetitions"] as? Number)?.toInt() ?: 0
                    val state = (cardData["state"] as? Number)?.toInt() ?: 0
                    val lastReview = (cardData["lastReviewTimestamp"] as? Number)?.toLong() ?: 0
                    val nextReview = (cardData["nextReviewTimestamp"] as? Number)?.toLong() ?: 0
                    val contentUpdatedAt = (cardData["contentUpdatedAt"] as? Number)?.toLong() ?: 0
                    val srsUpdatedAt = (cardData["srsUpdatedAt"] as? Number)?.toLong() ?: 0
                    val cardDeletedAt = (cardData["deletedAt"] as? Number)?.toLong()

                    cards.add(Flashcard(
                        id = cardId, pages = pages,
                        srsState = com.raycoll.app.domain.model.SrsState(
                            difficulty = difficulty, stability = stability,
                            repetitions = reps, state = state,
                            lastReviewTimestamp = lastReview, nextReviewTimestamp = nextReview
                        ),
                        contentUpdatedAt = contentUpdatedAt, srsUpdatedAt = srsUpdatedAt,
                        deletedAt = cardDeletedAt,
                    ))
                }

                groups.add(FlashcardGroup(
                    id = deckId, name = name, cards = cards,
                    activeModeId = activeModeId, studyFilter = studyFilter, cardOrder = cardOrder,
                    pageNames = pageNames, pageLanguages = pageLanguages,
                    activePageCount = activePageCount, updatedAt = updatedAt,
                    deletedAt = deletedAt, archivedAt = archivedAt,
                ))
            }

            // Load study modes
            val modesSnap = rootRef.collection(FirestoreSchema.PATH_MODES).get().await()
            for (modeDoc in modesSnap) {
                val modeData = modeDoc.data
                val modeId = modeData["id"] as? String ?: continue
                val modeName = modeData["name"] as? String ?: continue
                val isBuiltIn = (modeData["isBuiltIn"] as? Boolean) ?: false
                val builtInSourceId = modeData["builtInSourceId"] as? String
                val modeUpdatedAt = (modeData["updatedAt"] as? Number)?.toLong() ?: 0
                val modeDeletedAt = (modeData["deletedAt"] as? Number)?.toLong()
                @Suppress("UNCHECKED_CAST")
                val stepsJson = (modeData["stepsJson"] as? String) ?: "[]"
                val steps: List<com.raycoll.app.domain.model.ModeStep> = try {
                    kotlinx.serialization.json.Json.decodeFromString(stepsJson)
                } catch (_: Exception) { emptyList() }

                modes.add(StudyMode(
                    id = modeId, name = modeName, steps = steps,
                    isBuiltIn = isBuiltIn, builtInSourceId = builtInSourceId,
                    updatedAt = modeUpdatedAt, deletedAt = modeDeletedAt,
                ))
            }

            // Load activity heatmap
            val activitySnap = rootRef.collection(FirestoreSchema.PATH_ACTIVITY).get().await()
            for (dayDoc in activitySnap) {
                val count = (dayDoc.data["count"] as? Number)?.toInt() ?: 0
                if (count >= 0) heatmap[dayDoc.id] = count
            }

            val cloudData = StoreData(groups = groups, studyModes = modes, activityHeatmap = heatmap)
            cloudCache = cloudData
            _status.value = SyncStatus.IDLE
            cloudData
        } catch (e: Exception) {
            lastSyncError = e.message
            _status.value = SyncStatus.ERROR
            null
        }
    }

    suspend fun saveUserData(uid: String, data: StoreData) {
        _status.value = SyncStatus.SAVING
        try {
            val rootRef = firestore.collection(FirestoreSchema.PATH_USERS).document(uid)
            val batch = firestore.batch()

            // Root document with updatedAt
            batch.set(rootRef, mapOf("updatedAt" to System.currentTimeMillis()))

            // Write decks and their cards
            for (deck in data.groups) {
                val deckRef = rootRef.collection(FirestoreSchema.PATH_DECKS).document(deck.id)
                batch.set(deckRef, FirestoreSchema.deckToMap(deck))

                // Write cards as subcollection
                for (card in deck.cards) {
                    val cardRef = deckRef.collection(FirestoreSchema.PATH_CARDS).document(card.id)
                    batch.set(cardRef, FirestoreSchema.cardToMap(card))
                }
            }

            // Write study modes
            for (mode in data.studyModes) {
                val modeRef = rootRef.collection(FirestoreSchema.PATH_MODES).document(mode.id)
                val modeMap = FirestoreSchema.modeToMap(mode).toMutableMap()
                modeMap["stepsJson"] = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.raycoll.app.domain.model.ModeStep.serializer()),
                    mode.steps
                )
                batch.set(modeRef, modeMap)
            }

            // Write activity heatmap
            for ((dayKey, count) in data.activityHeatmap) {
                val activityRef = rootRef.collection(FirestoreSchema.PATH_ACTIVITY).document(dayKey)
                batch.set(activityRef, FirestoreSchema.activityToMap(dayKey, count))
            }

            batch.commit().await()
            cloudCache = data
            _status.value = SyncStatus.IDLE
        } catch (e: Exception) {
            lastSyncError = e.message
            _status.value = SyncStatus.ERROR
        }
    }

    suspend fun bootstrap(localData: StoreData, cloudData: StoreData?): StoreData {
        return if (cloudData != null) {
            val merged = mergeUserData(localData, cloudData)
            deckRepository.replaceAll(merged)
            cloudCache = cloudData
            merged
        } else {
            localData
        }
    }

    fun lastSyncError(): String? = lastSyncError
}
