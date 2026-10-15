package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreGenerationTest {
    private fun deck(id: String, cardIds: List<String> = listOf("c")) = FlashcardGroup(
        id,
        id,
        cardIds.map { Flashcard(it, listOf(it, "answer"), SrsState()) }
    )

    @Test
    fun `replacement removes old decks modes and activity in either merge direction`() {
        val old = StoreData(
            groups = listOf(deck("old")),
            studyModes = listOf(StudyMode("custom", "Custom")),
            activityHeatmap = mapOf("2026-10-14" to 50)
        )
        val fresh = StoreData(groups = listOf(deck("fresh")), replacementAt = 100, replacementId = "reset")
        for (merged in listOf(mergeUserData(old, fresh), mergeUserData(fresh, old))) {
            assertEquals(listOf("fresh"), merged.groups.map { it.id })
            assertFalse(merged.studyModes.any { it.id == "custom" })
            assertTrue(merged.activityHeatmap.isEmpty())
            assertEquals(100L, merged.replacementAt)
            assertEquals("reset", merged.replacementId)
        }
    }

    @Test
    fun `simultaneous replacements converge using their ids`() {
        val a = StoreData(groups = listOf(deck("a")), replacementAt = 100, replacementId = "a")
        val b = StoreData(groups = listOf(deck("b")), replacementAt = 100, replacementId = "b")
        assertEquals(mergeUserData(a, b), mergeUserData(b, a))
        assertEquals("b", mergeUserData(a, b).groups.single().id)
    }

    @Test
    fun `canonical cloud order converges while preserving newer local content`() {
        val local = StoreData(groups = listOf(deck("b", listOf("2", "1")), deck("a")))
        val cloud = StoreData(groups = listOf(deck("a"), deck("b", listOf("1", "2"))))
        val merged = mergeUserData(local, cloud, preferCloudOrder = true)
        assertEquals(listOf("a", "b"), merged.groups.map { it.id })
        assertEquals(listOf("1", "2"), merged.groups.last().cards.map { it.id })
        assertEquals(merged, mergeUserData(merged, cloud, preferCloudOrder = true))
        val edited = local.copy(groups = local.groups.map { it.copy(name = "edited", updatedAt = 100) })
        assertTrue(mergeUserData(edited, cloud, true).groups.all { it.name == "edited" })
    }

    @Test
    fun `legacy order remains local until positions have been migrated`() {
        val local = StoreData(groups = listOf(deck("z"), deck("a")))
        val cloud = local.copy(groups = local.groups.reversed())
        assertEquals(listOf("z", "a"), mergeUserData(local, cloud, false).groups.map { it.id })
    }
}
