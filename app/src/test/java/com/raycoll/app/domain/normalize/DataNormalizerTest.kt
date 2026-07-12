package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.createNewSrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataNormalizerTest {

    private fun makeCard(id: String = "c1", pages: List<String> = listOf("hello", "back")): Flashcard =
        Flashcard(id = id, pages = pages, srsState = createNewSrsState(), contentUpdatedAt = 0, srsUpdatedAt = 0)

    private fun makeGroup(
        id: String = "g1",
        name: String = "Mock Deck",
        cards: List<Flashcard> = emptyList(),
        pageNames: List<String> = listOf("Front", "Back"),
        pageLanguages: List<String> = listOf("en-US", "pl-PL"),
        activePageCount: Int = 2,
    ): FlashcardGroup = FlashcardGroup(
        id = id, name = name, cards = cards,
        activeModeId = "classic", studyFilter = DEFAULT_STUDY_FILTER, cardOrder = DEFAULT_CARD_ORDER,
        pageNames = pageNames, pageLanguages = pageLanguages,
        activePageCount = activePageCount, updatedAt = 0,
    )

    @Test
    fun `handles backup with NaN activePageCount and overflow pages`() {
        val group = makeGroup(
            id = "g2", name = "Unnormalized", cards = emptyList(),
            activePageCount = Int.MAX_VALUE,
            pageNames = listOf("Front", "Back", "Extra", "Overflow", "Too much", "Ignored"),
            pageLanguages = listOf("en-US"),
        )
        val data = StoreData(groups = listOf(group), studyModes = emptyList(), activityHeatmap = emptyMap())
        val normalized = normalizeStoreData(data)
        assertEquals(5, normalized.groups[0].activePageCount)
        assertEquals(6, normalized.groups[0].pageNames.size)
        assertEquals(6, normalized.groups[0].pageLanguages.size)
    }

    @Test
    fun `does not crash when study mode has non-array steps`() {
        val mode = StudyMode(id = "custom-broken", name = "Broken", steps = emptyList())
        val data = StoreData(groups = emptyList(), studyModes = listOf(mode), activityHeatmap = emptyMap())
        val result = normalizeStoreData(data)
        val broken = result.studyModes.find { it.id == "custom-broken" }
        assertEquals(0, broken?.steps?.size)
    }

    @Test
    fun `preserves edited built-in mode name and properties`() {
        val mode = StudyMode(
            id = "classic", builtInSourceId = "classic", isBuiltIn = true,
            name = "Edited Classic", steps = listOf(WaitStep(ms = 250)), updatedAt = 1,
        )
        val data = StoreData(groups = emptyList(), studyModes = listOf(mode), activityHeatmap = emptyMap())
        val result = normalizeStoreData(data)
        assertTrue(result.studyModes[0].isBuiltIn)
        assertEquals("classic", result.studyModes[0].builtInSourceId)
        assertEquals("Edited Classic", result.studyModes[0].name)
        assertEquals(1, result.studyModes[0].steps.size)
    }

    @Test
    fun `does not infer builtInSourceId for modes with built-in IDs but no sourceId`() {
        val mode = StudyMode(
            id = "classic", isBuiltIn = true,
            name = "Edited Classic", steps = emptyList(), updatedAt = 1,
        )
        val data = StoreData(groups = emptyList(), studyModes = listOf(mode), activityHeatmap = emptyMap())
        val result = normalizeStoreData(data)
        assertNull(result.studyModes[0].builtInSourceId)
    }

    @Test
    fun `normalizeCard with invalid srs state resets to 0`() {
        val card = Flashcard(
            id = "c1", pages = listOf("a", "b"),
            srsState = SrsState(state = 99),
        )
        val group = makeGroup(cards = listOf(card))
        val result = normalizeGroup(group)
        assertEquals(0, result.cards[0].srsState.state)
    }

    @Test
    fun `padPageMetadata fills to count`() {
        val (names, langs) = padPageMetadata(listOf("A"), listOf("en-US"), 3)
        assertEquals(listOf("A", "Page 2", "Page 3"), names)
        assertEquals(listOf("en-US", "en-US", "en-US"), langs)
    }

    @Test
    fun `normalizeActivityHeatmap filters invalid entries`() {
        val raw = mapOf("2024-01-01" to 5, "bad" to (-1), "nan" to Int.MAX_VALUE)
        val result = normalizeActivityHeatmap(raw)
        assertEquals(2, result.size)
        assertEquals(5, result["2024-01-01"])
    }
}
