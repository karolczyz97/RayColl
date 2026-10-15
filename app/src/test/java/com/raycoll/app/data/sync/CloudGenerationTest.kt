package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudGenerationTest {
    private val cards = (0..800).map { Flashcard("c$it", listOf("front", "back"), SrsState()) }
    private val store = StoreData(
        groups = listOf(FlashcardGroup("deck", "Deck", cards)),
        studyModes = listOf(StudyMode("mode", "Mode")),
        activityHeatmap = mapOf("2026-10-14" to 5)
    )

    @Test fun `legacy documents remain visible to the initial zero generation`() {
        assertEquals(store, visibleCloudGeneration(store, emptyMap()))
    }

    @Test fun `a partial replacement never exposes remaining old cards modes or activity`() {
        val next = store.copy(replacementAt = 20, replacementId = "reset")
        val generations = mapOf(deckKey("deck") to (20L to "reset")) +
            cards.take(399).associate { cardKey("deck", it.id) to (20L to "reset") }
        val visible = visibleCloudGeneration(next, generations)
        assertEquals(cards.take(399), visible.groups.single().cards)
        assertTrue(visible.studyModes.isEmpty())
        assertTrue(visible.activityHeatmap.isEmpty())
    }

    @Test fun `replacement rewrites surviving documents even if their values have not changed`() {
        val next = store.copy(replacementAt = 20, replacementId = "reset")
        val ops = SyncDiff.diffSave(store, next)
        assertEquals(801, ops.filterIsInstance<SyncOp.PutCard>().size)
        assertEquals(1, ops.filterIsInstance<SyncOp.PutDeck>().size)
        assertEquals(1, ops.filterIsInstance<SyncOp.PutMode>().size)
        assertEquals(1, ops.filterIsInstance<SyncOp.PutActivity>().size)
    }

    @Test fun `retry rewrites identical stale documents and removes abandoned old documents`() {
        val next = store.copy(replacementAt = 20, replacementId = "reset")
        val generations = buildMap {
            put(deckKey("deck"), 20L to "reset")
            cards.forEach { put(cardKey("deck", it.id), 0L to "") }
            put(modeKey("mode"), 0L to "")
            put(activityKey("2026-10-14"), 0L to "")
        }
        val keys = staleGenerationKeys(next, generations)
        val retry = SyncDiff.diffSave(next, next, keys)
        assertEquals(801, retry.filterIsInstance<SyncOp.PutCard>().size)
        assertEquals(1, retry.filterIsInstance<SyncOp.PutMode>().size)
        val visible = visibleCloudGeneration(next, generations)
        assertEquals(801, SyncDiff.diffSave(next, visible, keys).filterIsInstance<SyncOp.DeleteCard>().size)
    }
}
