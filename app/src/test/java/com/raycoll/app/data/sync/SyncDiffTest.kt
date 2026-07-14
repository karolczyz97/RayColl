package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiffTest {

    private fun card(id: String, reps: Int = 0) = Flashcard(
        id = id, pages = listOf("f", "b"), srsState = SrsState(repetitions = reps),
    )

    private fun deck(id: String, cards: List<Flashcard>, name: String = "Deck $id") = FlashcardGroup(
        id = id, name = name, cards = cards, pageNames = listOf("A", "B"), pageLanguages = listOf("en-US", "pl-PL"),
    )

    private fun mode(id: String, name: String = "M$id") = StudyMode(id = id, name = name)

    @Test
    fun `fullSave pisze wszystko i kasuje osierocone dokumenty`() {
        val existing = StoreData(
            groups = listOf(deck("d1", listOf(card("c1"), card("c2"))), deck("dOld", listOf(card("cOld")))),
            studyModes = listOf(mode("m1"), mode("mOld")),
            activityHeatmap = mapOf("2026-01-01" to 3, "2025-12-31" to 1),
        )
        val data = StoreData(
            groups = listOf(deck("d1", listOf(card("c1")))),
            studyModes = listOf(mode("m1")),
            activityHeatmap = mapOf("2026-01-01" to 5),
        )
        val ops = SyncDiff.fullSave(existing, data)

        assertTrue(ops.contains(SyncOp.PutDeck(data.groups[0])))
        assertTrue(ops.contains(SyncOp.PutCard("d1", card("c1"))))
        assertTrue(ops.contains(SyncOp.DeleteCard("d1", "c2")))
        assertTrue(ops.contains(SyncOp.DeleteCard("dOld", "cOld")))
        assertTrue(ops.contains(SyncOp.DeleteDeck("dOld")))
        assertTrue(ops.contains(SyncOp.PutMode(mode("m1"))))
        assertTrue(ops.contains(SyncOp.DeleteMode("mOld")))
        assertTrue(ops.contains(SyncOp.PutActivity("2026-01-01", 5)))
        assertTrue(ops.contains(SyncOp.DeleteActivity("2025-12-31")))
    }

    @Test
    fun `fullSave od pustej chmury nie generuje kasowań`() {
        val data = StoreData(groups = listOf(deck("d1", listOf(card("c1")))), studyModes = listOf(mode("m1")))
        val ops = SyncDiff.fullSave(StoreData(), data)
        assertTrue(ops.none { it is SyncOp.DeleteDeck || it is SyncOp.DeleteCard || it is SyncOp.DeleteMode || it is SyncOp.DeleteActivity })
        assertEquals(3, ops.size) // deck + card + mode
    }

    @Test
    fun `diffSave pisze tylko zmienione dokumenty`() {
        val d1 = deck("d1", listOf(card("c1"), card("c2")))
        val previous = StoreData(
            groups = listOf(d1),
            studyModes = listOf(mode("m1")),
            activityHeatmap = mapOf("2026-01-01" to 3),
        )
        // zmiana TYLKO SRS jednej karty
        val next = previous.copy(
            groups = listOf(d1.copy(cards = listOf(card("c1", reps = 1), card("c2")))),
        )
        val ops = SyncDiff.diffSave(previous, next)
        assertEquals(listOf<SyncOp>(SyncOp.PutCard("d1", card("c1", reps = 1))), ops)
    }

    @Test
    fun `diffSave metadanych talii nie przepisuje kart`() {
        val d1 = deck("d1", listOf(card("c1")))
        val previous = StoreData(groups = listOf(d1))
        val next = StoreData(groups = listOf(d1.copy(name = "Nowa nazwa")))
        val ops = SyncDiff.diffSave(previous, next)
        assertEquals(listOf<SyncOp>(SyncOp.PutDeck(next.groups[0])), ops)
    }

    @Test
    fun `diffSave kasuje usunięte karty, talie, tryby i dni`() {
        val previous = StoreData(
            groups = listOf(deck("d1", listOf(card("c1"), card("c2"))), deck("d2", listOf(card("c3")))),
            studyModes = listOf(mode("m1")),
            activityHeatmap = mapOf("2026-01-01" to 3),
        )
        val next = StoreData(groups = listOf(deck("d1", listOf(card("c1")))))
        val ops = SyncDiff.diffSave(previous, next)
        assertTrue(ops.contains(SyncOp.DeleteCard("d1", "c2")))
        assertTrue(ops.contains(SyncOp.DeleteCard("d2", "c3")))
        assertTrue(ops.contains(SyncOp.DeleteDeck("d2")))
        assertTrue(ops.contains(SyncOp.DeleteMode("m1")))
        assertTrue(ops.contains(SyncOp.DeleteActivity("2026-01-01")))
        // d1 bez zmian metadanych, c1 bez zmian — zero putów
        assertTrue(ops.none { it is SyncOp.PutDeck || it is SyncOp.PutCard })
    }

    @Test
    fun `diffSave bez zmian daje zero operacji`() {
        val state = StoreData(
            groups = listOf(deck("d1", listOf(card("c1")))),
            studyModes = listOf(mode("m1")),
            activityHeatmap = mapOf("2026-01-01" to 3),
        )
        assertEquals(emptyList<SyncOp>(), SyncDiff.diffSave(state, state))
    }
}
