package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataNormalizerTest {

    @Test
    fun `normalizeCard clamps invalid SRS state to 0`() {
        val card = Flashcard(id = "c1", pages = listOf("a", "b"), srsState = SrsState(state = 99))
        val result = normalizeCard(card)
        assertEquals(0, result.srsState.state)
    }

    @Test
    fun `normalizeCard preserves valid SRS state`() {
        val card = Flashcard(id = "c1", pages = listOf("a", "b"), srsState = SrsState(state = 2))
        val result = normalizeCard(card)
        assertEquals(2, result.srsState.state)
    }

    @Test
    fun `padPageMetadata pads names and languages to count`() {
        val (names, langs) = padPageMetadata(listOf("Front"), listOf("en-US"), 3)
        assertEquals(listOf("Front", "Page 2", "Page 3"), names)
        assertEquals(listOf("en-US", "en-US", "en-US"), langs)
    }

    @Test
    fun `normalizeGroup handles NaN activePageCount`() {
        val group = FlashcardGroup(
            id = "g1", name = "Test",
            pageNames = listOf("Front", "Back", "Extra", "Overflow", "TooMuch", "Ignored"),
            pageLanguages = listOf("en-US"),
            activePageCount = 2,
            cards = emptyList()
        )
        val result = normalizeGroup(group)
        assertTrue(result.activePageCount in 2..5)
        assertEquals(6, result.pageNames.size)
        assertEquals(6, result.pageLanguages.size)
    }

    @Test
    fun `normalizeStudyMode handles non-list steps`() {
        val mode = StudyMode(id = "broken", name = "Broken", steps = emptyList(), isBuiltIn = false)
        val result = normalizeStudyMode(mode)
        assertEquals(0, result.steps.size)
    }

    @Test
    fun `normalizeStudyMode preserves edited built-in mode name`() {
        val mode = StudyMode(
            id = "classic", name = "Edited Classic", isBuiltIn = true,
            builtInSourceId = "classic", updatedAt = 1L,
            steps = listOf(Wait(ms = 250))
        )
        val result = normalizeStudyMode(mode)
        assertTrue(result.isBuiltIn)
        assertEquals("classic", result.builtInSourceId)
        assertEquals("Edited Classic", result.name)
        assertEquals(1, result.steps.size)
    }

    @Test
    fun `normalizeStudyMode does not infer builtInSourceId without isBuiltIn flag`() {
        val mode = StudyMode(
            id = "classic", name = "Edited Classic", isBuiltIn = false,
            updatedAt = 1L, steps = emptyList()
        )
        val result = normalizeStudyMode(mode)
        assertNull(result.builtInSourceId)
    }

    @Test
    fun `normalizeStoreData normalizes groups and modes`() {
        val data = StoreData(
            groups = listOf(FlashcardGroup(id = "g1", name = "Test", pageNames = listOf("A", "B"), pageLanguages = listOf("en-US", "en-US"))),
            studyModes = listOf(StudyMode(id = "m1", name = "Mode", steps = emptyList())),
            activityHeatmap = mapOf("2026-01-01" to 5)
        )
        val result = normalizeStoreData(data)
        assertEquals(1, result.groups.size)
        assertEquals(1, result.studyModes.size)
        assertEquals(5, result.activityHeatmap["2026-01-01"])
    }

    @Test
    fun `normalizeActivityHeatmap filters zero counts`() {
        val heatmap = mapOf("a" to 1, "b" to 0, "c" to 3)
        val result = normalizeActivityHeatmap(heatmap)
        assertEquals(2, result.size)
        assertFalse(result.containsKey("b"))
    }

    @Test
    fun `getStoredPageCount uses max of all sources`() {
        val group = FlashcardGroup(
            id = "g1", name = "Test",
            cards = listOf(Flashcard(id = "c1", pages = listOf("a", "b", "c"))),
            pageNames = listOf("A"), pageLanguages = listOf("en")
        )
        assertEquals(3, getStoredPageCount(group, group.pageNames, group.pageLanguages))
    }
}
