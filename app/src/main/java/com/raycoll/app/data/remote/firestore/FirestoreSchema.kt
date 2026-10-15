package com.raycoll.app.data.remote.firestore

import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.keyedEnumOrNull
import com.raycoll.app.domain.normalize.assertStudyModeStep
import com.raycoll.app.domain.normalize.jsonElementToPlain
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class FirestoreDocException(message: String) : Exception(message)

/**
 * Format zachowuje zgodność z dokumentami już zapisanymi w Firestore;
 * jego zmiana wymaga migracji danych użytkowników:
 * - karta ma ZAGNIEŻDŻONY obiekt `srsState` (nie płaskie pola),
 * - tryb ma STRUKTURALNĄ tablicę `steps` (nie string JSON),
 * - `deletedAt`/`archivedAt`/`builtInSourceId` zapisywane tylko gdy obecne,
 * - `position` (talie i karty) = miejsce na liście; opcjonalne, bo starsze dokumenty go nie mają,
 * - deserializacja jest STRICT (brak/zły typ pola → wyjątek); loader per-dokument
 *   łapie wyjątek i pomija uszkodzony dokument zamiast wywalać cały load.
 *   Pola opcjonalne (deletedAt, archivedAt, position) są łagodne: zły typ = brak.
 */
object FirestoreSchema {
    const val PATH_USERS = "users"
    const val PATH_DECKS = "decks"
    const val PATH_CARDS = "cards"
    const val PATH_MODES = "studyModes"
    const val PATH_ACTIVITY = "activity"

    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_REPLACEMENT_AT = "replacementAt"
    const val FIELD_REPLACEMENT_ID = "replacementId"
    private const val FIELD_POSITION = "position"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val stepsSerializer = ListSerializer(ModeStep.serializer())

    // ---------- serializacja (Kotlin → mapy Firestore) ----------

    fun deckToMap(deck: FlashcardGroup, position: Int? = null): Map<String, Any> = buildMap {
        put("id", deck.id)
        put("name", deck.name)
        put("activeModeId", deck.activeModeId)
        put("pageLanguages", deck.pageLanguages)
        put("pageNames", deck.pageNames)
        put("activePageCount", deck.activePageCount)
        put("studyFilter", deck.studyFilter.key)
        put("cardOrder", deck.cardOrder.key)
        put(FIELD_UPDATED_AT, deck.updatedAt)
        deck.deletedAt?.let { put("deletedAt", it) }
        deck.archivedAt?.let { put("archivedAt", it) }
        position?.let { put(FIELD_POSITION, it) }
    }

    fun cardToMap(card: Flashcard, position: Int? = null): Map<String, Any> = buildMap {
        put("id", card.id)
        put("pages", card.pages)
        put(
            "srsState",
            mapOf(
                "difficulty" to card.srsState.difficulty,
                "stability" to card.srsState.stability,
                "repetitions" to card.srsState.repetitions,
                "state" to card.srsState.state,
                "lastReviewTimestamp" to card.srsState.lastReviewTimestamp,
                "nextReviewTimestamp" to card.srsState.nextReviewTimestamp
            )
        )
        put("contentUpdatedAt", card.contentUpdatedAt)
        put("srsUpdatedAt", card.srsUpdatedAt)
        card.deletedAt?.let { put("deletedAt", it) }
        position?.let { put(FIELD_POSITION, it) }
    }

    fun modeToMap(mode: StudyMode): Map<String, Any> = buildMap {
        put("id", mode.id)
        put("name", mode.name)
        put("steps", stepsToPlain(mode.steps))
        put("isBuiltIn", mode.isBuiltIn)
        // pusty identyfikator źródła traktowany jak brak w zapisanych dokumentach
        mode.builtInSourceId?.takeIf { it.isNotEmpty() }?.let { put("builtInSourceId", it) }
        put(FIELD_UPDATED_AT, mode.updatedAt)
        mode.deletedAt?.let { put("deletedAt", it) }
    }

    fun activityToMap(count: Int): Map<String, Any> = mapOf("count" to count)

    // ---------- deserializacja (mapy Firestore → Kotlin, strict) ----------

    fun deckFromMap(docId: String, data: Map<String, Any?>): FlashcardGroup {
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
        val studyFilter = keyedEnumOrNull(StudyFilter.entries, data["studyFilter"] as? String)
            ?: throw FirestoreDocException("Firestore deck $docId has invalid studyFilter.")
        val cardOrder = keyedEnumOrNull(CardOrder.entries, data["cardOrder"] as? String)
            ?: throw FirestoreDocException("Firestore deck $docId has invalid cardOrder.")
        return FlashcardGroup(
            id = id,
            name = requireString(data["name"], "name for deck $docId"),
            cards = emptyList(),
            activeModeId = requireString(data["activeModeId"], "activeModeId for deck $docId"),
            pageLanguages = requireStringList(data["pageLanguages"], "pageLanguages for deck $docId"),
            pageNames = requireStringList(data["pageNames"], "pageNames for deck $docId"),
            activePageCount = requireFiniteNumber(data["activePageCount"], "activePageCount for deck $docId").toInt(),
            studyFilter = studyFilter,
            cardOrder = cardOrder,
            updatedAt = requireFiniteNumber(data["updatedAt"], "updatedAt for deck $docId").toLong(),
            deletedAt = optionalLong(data["deletedAt"]),
            archivedAt = optionalLong(data["archivedAt"])
        )
    }

    fun cardFromMap(docId: String, data: Map<String, Any?>): Flashcard {
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
        val srs = data["srsState"] as? Map<*, *>
            ?: throw FirestoreDocException("Firestore card $docId is missing a valid srsState.")
        return Flashcard(
            id = id,
            pages = requireStringList(data["pages"], "pages for card $docId"),
            srsState = SrsState(
                difficulty = requireFiniteNumber(srs["difficulty"], "srsState.difficulty for card $docId").toDouble(),
                stability = requireFiniteNumber(srs["stability"], "srsState.stability for card $docId").toDouble(),
                repetitions = requireFiniteNumber(srs["repetitions"], "srsState.repetitions for card $docId").toInt(),
                state = requireFiniteNumber(srs["state"], "srsState.state for card $docId").toInt(),
                lastReviewTimestamp = requireFiniteNumber(
                    srs["lastReviewTimestamp"],
                    "srsState.lastReviewTimestamp for card $docId"
                ).toLong(),
                nextReviewTimestamp = requireFiniteNumber(
                    srs["nextReviewTimestamp"],
                    "srsState.nextReviewTimestamp for card $docId"
                ).toLong()
            ),
            contentUpdatedAt = requireFiniteNumber(
                data["contentUpdatedAt"],
                "contentUpdatedAt for card $docId"
            ).toLong(),
            srsUpdatedAt = requireFiniteNumber(data["srsUpdatedAt"], "srsUpdatedAt for card $docId").toLong(),
            deletedAt = optionalLong(data["deletedAt"])
        )
    }

    fun modeFromMap(docId: String, data: Map<String, Any?>): StudyMode {
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
        val rawSteps = data["steps"] as? List<*>
            ?: throw FirestoreDocException("Firestore study mode $docId is missing steps.")
        rawSteps.forEachIndexed { index, step -> assertStudyModeStep(step, id, index) }
        val isBuiltIn = data["isBuiltIn"] as? Boolean
            ?: throw FirestoreDocException("Firestore study mode $id has an invalid isBuiltIn flag.")
        val builtInSourceId = data["builtInSourceId"]
        if (isBuiltIn && (builtInSourceId !is String || builtInSourceId.isBlank())) {
            throw FirestoreDocException("Firestore study mode $id has an invalid builtInSourceId.")
        }
        if (!isBuiltIn && builtInSourceId != null) {
            throw FirestoreDocException("Firestore study mode $id has an invalid builtInSourceId.")
        }
        return StudyMode(
            id = id,
            name = requireString(data["name"], "name for study mode $id"),
            steps = json.decodeFromJsonElement(stepsSerializer, plainToJsonElement(rawSteps)),
            isBuiltIn = isBuiltIn,
            builtInSourceId = builtInSourceId as? String,
            updatedAt = requireFiniteNumber(data["updatedAt"], "updatedAt for study mode $id").toLong(),
            deletedAt = optionalLong(data["deletedAt"])
        )
    }

    /** Miejsce dokumentu talii/karty na liście; null w starszych dokumentach. */
    fun positionFromMap(data: Map<String, Any?>): Long? = optionalLong(data[FIELD_POSITION])

    /** Brak znacznika oznacza konto utworzone przed wprowadzeniem zastępowania danych. */
    fun replacementFromMap(data: Map<String, Any?>): Pair<Long, String> {
        if (FIELD_REPLACEMENT_AT !in data && FIELD_REPLACEMENT_ID !in data) return 0L to ""
        val at = data[FIELD_REPLACEMENT_AT]
        val id = data[FIELD_REPLACEMENT_ID]
        if (at !is Number || !at.toDouble().isFinite() || at.toDouble() < 0 ||
            at.toDouble() >= Long.MAX_VALUE.toDouble() ||
            at.toDouble() != at.toLong().toDouble() || id !is String || (at.toLong() > 0 && id.isBlank())
        ) {
            throw FirestoreDocException("Firestore account has an invalid replacement marker.")
        }
        return at.toLong() to id
    }

    fun activityCountFromMap(docId: String, data: Map<String, Any?>): Int {
        val count = data["count"]
        if (count !is Number || !count.toDouble().isFinite()) {
            throw FirestoreDocException("Firestore activity day $docId is missing a valid count.")
        }
        return count.toInt()
    }

    // ---------- pomocnicze ----------

    private fun requireString(value: Any?, label: String): String {
        if (value !is String || value.isBlank()) {
            throw FirestoreDocException("Firestore document is missing a valid $label.")
        }
        return value
    }

    private fun requireFiniteNumber(value: Any?, label: String): Number {
        if (value !is Number || !value.toDouble().isFinite()) {
            throw FirestoreDocException("Firestore document is missing a valid $label.")
        }
        return value
    }

    private fun requireStringList(value: Any?, label: String): List<String> {
        if (value !is List<*> || value.any { it !is String }) {
            throw FirestoreDocException("Firestore document is missing a valid $label.")
        }
        @Suppress("UNCHECKED_CAST")
        return value as List<String>
    }

    private fun optionalLong(value: Any?): Long? = (value as? Number)?.toLong()

    /** Kroki trybu → lista map bez null, zgodna z zapisanymi dokumentami chmury. */
    internal fun stepsToPlain(steps: List<ModeStep>): List<Any?> {
        val element = json.encodeToJsonElement(stepsSerializer, steps)
        return dropNulls(jsonElementToPlain(element)) as List<Any?>
    }

    private fun dropNulls(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.filterValues { it != null }.mapValues { dropNulls(it.value) }
        is List<*> -> value.map { dropNulls(it) }
        else -> value
    }

    /** Odwrotność jsonElementToPlain — czyste struktury → JsonElement do dekodera. */
    internal fun plainToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is String -> JsonPrimitive(value)

        is Boolean -> JsonPrimitive(value)

        is Int -> JsonPrimitive(value)

        is Long -> JsonPrimitive(value)

        is Number -> {
            // liczby całkowite z Firestore przychodzą jako Long, ale zabezpiecz
            // Double o wartości całkowitej (2.0 → 2), żeby dekoder Int nie odrzucił
            val d = value.toDouble()
            if (d == floor(d) && !d.isInfinite() && abs(d) < Long.MAX_VALUE.toDouble()) {
                JsonPrimitive(d.toLong())
            } else {
                JsonPrimitive(d)
            }
        }

        is List<*> -> JsonArray(value.map { plainToJsonElement(it) })

        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to plainToJsonElement(v) })

        else -> throw FirestoreDocException("Unsupported Firestore value type: ${value::class.simpleName}")
    }
}
