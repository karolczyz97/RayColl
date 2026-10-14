package com.raycoll.app.data.remote.firestore

import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StepCondition
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.StudyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zgodność z zapisanym formatem dokumentów: zagnieżdżony srsState,
 * strukturalne steps, opcjonalne pola tylko gdy obecne, strict deserializacja.
 */
class FirestoreSchemaTest {

    private fun card(deleted: Long? = null) = Flashcard(
        id = "c1",
        pages = listOf("front", "back"),
        srsState = SrsState(
            difficulty = 5.5,
            stability = 2.0,
            repetitions = 3,
            state = 2,
            lastReviewTimestamp = 100L,
            nextReviewTimestamp = 200L
        ),
        contentUpdatedAt = 10L,
        srsUpdatedAt = 20L,
        deletedAt = deleted
    )

    private fun deck() = FlashcardGroup(
        id = "d1", name = "Deck", cards = listOf(card()),
        activeModeId = "classic", studyFilter = StudyFilter.NEW_AND_REVIEW, cardOrder = CardOrder.SEQUENTIAL,
        pageLanguages = listOf("en-US", "pl-PL"), pageNames = listOf("A", "B"),
        activePageCount = 2, updatedAt = 42L
    )

    private fun mode() = StudyMode(
        id = "m1",
        name = "Tryb",
        steps = listOf(
            ShowPageStep(pageIndex = 0),
            ListenAndCheckStep(pageIndex = 1, successThreshold = 70, condition = StepCondition.CORRECT),
            CompoundStep(params = CompoundParams.ListenGrade()),
            ShowRatingsStep()
        ),
        isBuiltIn = false,
        updatedAt = 7L
    )

    // ---------- serializacja ----------

    @Test
    fun `cardToMap ma zagnieżdżony srsState a nie płaskie pola`() {
        val map = FirestoreSchema.cardToMap(card())
        val srs = map["srsState"] as Map<*, *>
        assertEquals(5.5, srs["difficulty"])
        assertEquals(3, srs["repetitions"])
        assertFalse(map.containsKey("difficulty"))
        assertFalse(map.containsKey("deletedAt")) // null pomijany
        assertTrue(FirestoreSchema.cardToMap(card(deleted = 99L)).containsKey("deletedAt"))
    }

    @Test
    fun `deckToMap pomija null deletedAt-archivedAt i nie zawiera kart`() {
        val map = FirestoreSchema.deckToMap(deck())
        assertFalse(map.containsKey("deletedAt"))
        assertFalse(map.containsKey("archivedAt"))
        assertFalse(map.containsKey("cards"))
        assertEquals("new+review", map["studyFilter"])
    }

    @Test
    fun `modeToMap ma strukturalne steps a nie stepsJson`() {
        val map = FirestoreSchema.modeToMap(mode())
        assertFalse(map.containsKey("stepsJson"))
        val steps = map["steps"] as List<*>
        assertEquals(4, steps.size)
        val first = steps[0] as Map<*, *>
        assertEquals("show_page", first["type"])
        assertEquals(0L, (first["pageIndex"] as Number).toLong())
        // Pusty warunek nie jest zapisywany w dokumencie chmury.
        assertFalse(first.containsKey("condition"))
        val second = steps[1] as Map<*, *>
        assertEquals("correct", second["condition"])
        // builtInSourceId pomijany gdy null
        assertFalse(map.containsKey("builtInSourceId"))
    }

    // ---------- round-trip ----------

    @Test
    fun `round-trip deck+card+mode 1-do-1`() {
        val deck = deck()
        val cardBack = FirestoreSchema.cardFromMap("c1", FirestoreSchema.cardToMap(card()))
        assertEquals(card(), cardBack)
        val deckBack = FirestoreSchema.deckFromMap("d1", FirestoreSchema.deckToMap(deck), listOf(cardBack))
        assertEquals(deck, deckBack)
        val mode = mode()
        val modeBack = FirestoreSchema.modeFromMap("m1", FirestoreSchema.modeToMap(mode))
        assertEquals(mode, modeBack)
    }

    @Test
    fun `round-trip activity`() {
        assertEquals(5, FirestoreSchema.activityCountFromMap("2026-01-01", FirestoreSchema.activityToMap(5)))
    }

    // ---------- strict deserializacja ----------

    @Test
    fun `karta bez srsState odrzucona`() {
        val map = FirestoreSchema.cardToMap(card()).toMutableMap()
        map.remove("srsState")
        assertThrows(Exception::class.java) { FirestoreSchema.cardFromMap("c1", map) }
    }

    @Test
    fun `karta z nie-liczbowym polem srs odrzucona`() {
        val map = FirestoreSchema.cardToMap(card()).toMutableMap()
        map["srsState"] = mapOf("difficulty" to "zle")
        assertThrows(Exception::class.java) { FirestoreSchema.cardFromMap("c1", map) }
    }

    @Test
    fun `deck ze złym studyFilter odrzucony`() {
        val map = FirestoreSchema.deckToMap(deck()).toMutableMap()
        map["studyFilter"] = "everything"
        assertThrows(Exception::class.java) { FirestoreSchema.deckFromMap("d1", map, emptyList()) }
    }

    @Test
    fun `deck bez updatedAt odrzucony`() {
        val map = FirestoreSchema.deckToMap(deck()).toMutableMap()
        map.remove("updatedAt")
        assertThrows(Exception::class.java) { FirestoreSchema.deckFromMap("d1", map, emptyList()) }
    }

    @Test
    fun `mode bez steps odrzucony`() {
        val map = FirestoreSchema.modeToMap(mode()).toMutableMap()
        map.remove("steps")
        assertThrows(Exception::class.java) { FirestoreSchema.modeFromMap("m1", map) }
    }

    @Test
    fun `mode z nieznanym typem kroku odrzucony`() {
        val map = FirestoreSchema.modeToMap(mode()).toMutableMap()
        map["steps"] = listOf(mapOf("type" to "hyper_step"))
        assertThrows(Exception::class.java) { FirestoreSchema.modeFromMap("m1", map) }
    }

    @Test
    fun `builtIn bez builtInSourceId odrzucony, nie-builtIn z sourceId też`() {
        val builtIn = FirestoreSchema.modeToMap(
            mode().copy(isBuiltIn = true, builtInSourceId = "classic")
        ).toMutableMap()
        builtIn.remove("builtInSourceId")
        assertThrows(Exception::class.java) { FirestoreSchema.modeFromMap("m1", builtIn) }

        val notBuiltIn = FirestoreSchema.modeToMap(mode()).toMutableMap()
        notBuiltIn["builtInSourceId"] = "classic"
        assertThrows(Exception::class.java) { FirestoreSchema.modeFromMap("m1", notBuiltIn) }
    }

    @Test
    fun `puste id dokumentu spada na docId`() {
        val map = FirestoreSchema.cardToMap(card()).toMutableMap()
        map["id"] = ""
        assertEquals("doc-id", FirestoreSchema.cardFromMap("doc-id", map).id)
    }

    @Test
    fun `activity bez count odrzucone`() {
        assertThrows(Exception::class.java) {
            FirestoreSchema.activityCountFromMap("2026-01-01", mapOf("updatedAt" to 1L))
        }
    }

    @Test
    fun `liczby Double z Firestore dekodują się do pól Int w krokach`() {
        // Firestore potrafi zwrócić 1.0 zamiast 1 — dekoder nie może tego odrzucić
        val map = FirestoreSchema.modeToMap(mode()).toMutableMap()
        map["steps"] = listOf(mapOf("type" to "show_page", "pageIndex" to 1.0))
        val decoded = FirestoreSchema.modeFromMap("m1", map)
        assertEquals(ShowPageStep(pageIndex = 1), decoded.steps[0])
        assertNull((decoded.steps[0] as ShowPageStep).condition)
    }
}
