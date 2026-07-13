package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundPause
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
    fun `normalizes compound listen_grade branches (speakPage, rating, pause, rate)`() {
        val corrupt = CompoundStep(
            params = CompoundParams.ListenGrade(
                answerPage = -3,
                threshold = 250,
                onCorrect = CompoundBranch(
                    speakPage = -5,
                    fixedRating = 99,
                    rate = "bogus",
                    pause = CompoundPause.Dynamic(page = -2, multiplier = 42.0),
                ),
                onWrong = CompoundBranch(
                    speakPage = 7,
                    fixedRating = 0,
                    rate = "nonsense",
                    pause = CompoundPause.Fixed(ms = -100),
                ),
            ),
        )
        val mode = StudyMode(id = "m-compound", name = "Compound", steps = listOf(corrupt))
        val data = StoreData(groups = emptyList(), studyModes = listOf(mode), activityHeatmap = emptyMap())
        val result = normalizeStoreData(data)
        val params = (result.studyModes[0].steps[0] as CompoundStep).params as CompoundParams.ListenGrade

        assertEquals(0, params.answerPage)          // page clamp >= 0
        assertEquals(100, params.threshold)         // 0..100
        // correct branch
        assertEquals(0, params.onCorrect.speakPage)         // -5 → 0
        assertEquals(4, params.onCorrect.fixedRating)       // 99 → 4
        assertEquals("fromAnswer", params.onCorrect.rate)   // bogus → default (correct)
        assertEquals(0, (params.onCorrect.pause as CompoundPause.Dynamic).page)
        assertEquals(5.0, (params.onCorrect.pause as CompoundPause.Dynamic).multiplier, 0.0)
        // wrong branch
        assertEquals(1, params.onWrong.fixedRating)         // 0 → 1
        assertEquals("fixed", params.onWrong.rate)          // nonsense → default (wrong)
        assertEquals(0L, (params.onWrong.pause as CompoundPause.Fixed).ms) // -100 → 0
    }

    @Test
    fun `normalizeActivityHeatmap filters invalid entries`() {
        val raw = mapOf("2024-01-01" to 5, "bad" to (-1), "nan" to Int.MAX_VALUE)
        val result = normalizeActivityHeatmap(raw)
        assertEquals(2, result.size)
        assertEquals(5, result["2024-01-01"])
    }
}
