package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port testów MergeEngine z równoległej implementacji "RayColl - Kopie" (spec = merge.ts). */
class MergeEngineTest {

    private val now = 1000000000000L

    private fun makeCard(
        id: String,
        pages: List<String> = listOf("front", "back"),
        repetitions: Int = 0,
        contentUpdatedAt: Long = 0L,
        srsUpdatedAt: Long = 0L,
        deletedAt: Long? = null,
    ) = Flashcard(
        id = id,
        pages = pages,
        srsState = SrsState(repetitions = repetitions),
        contentUpdatedAt = contentUpdatedAt,
        srsUpdatedAt = srsUpdatedAt,
        deletedAt = deletedAt,
    )

    private fun makeDeck(
        id: String,
        name: String = "Test Deck",
        cards: List<Flashcard> = emptyList(),
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
    ) = FlashcardGroup(
        id = id,
        name = name,
        cards = cards,
        pageLanguages = listOf("en-US", "pl-PL"),
        pageNames = listOf("Front", "Back"),
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun `mergeActivityHeatmap takes max per day and union of days`() {
        val local = mapOf("2026-05-27" to 3, "2026-05-28" to 2)
        val cloud = mapOf("2026-05-27" to 1, "2026-05-29" to 5)
        val expected = mapOf("2026-05-27" to 3, "2026-05-28" to 2, "2026-05-29" to 5)
        assertEquals(expected, mergeActivityHeatmap(local, cloud))
    }

    @Test
    fun `mergeCards content last-write-wins srs never-regress`() {
        val localCard = makeCard("c1", pages = listOf("local", "card"), repetitions = 4, contentUpdatedAt = now - 2000, srsUpdatedAt = now)
        val cloudCard = makeCard("c1", pages = listOf("cloud", "card"), repetitions = 1, contentUpdatedAt = now - 1000, srsUpdatedAt = 0L)

        val result = mergeCards(listOf(localCard), listOf(cloudCard))
        assertEquals(1, result.size)
        assertEquals("cloud", result[0].pages[0])
        assertEquals(4, result[0].srsState.repetitions)
    }

    @Test
    fun `mergeCards srs prefers higher repetitions then newer srsUpdatedAt`() {
        // wyższe repetitions wygrywają mimo starszego srsUpdatedAt
        val localHigh = makeCard("c1", repetitions = 4, srsUpdatedAt = 0L)
        val cloudLow = makeCard("c1", repetitions = 1, srsUpdatedAt = now)
        assertEquals(4, mergeCards(listOf(localHigh), listOf(cloudLow))[0].srsState.repetitions)

        // przy remisie repetitions decyduje nowszy srsUpdatedAt
        val localNewer = makeCard("c2", repetitions = 3, srsUpdatedAt = now).copy(pages = listOf("L", "x"))
        val cloudOlder = makeCard("c2", repetitions = 3, srsUpdatedAt = 0L).copy(pages = listOf("C", "x"))
        assertEquals(now, mergeCards(listOf(localNewer), listOf(cloudOlder))[0].srsUpdatedAt)
    }

    @Test
    fun `mergeCards uses srs side as content tiebreaker`() {
        val localCard = makeCard("c2", pages = listOf("local-tie", "page"), repetitions = 5, contentUpdatedAt = now, srsUpdatedAt = now)
        val cloudCard = makeCard("c2", pages = listOf("cloud-tie", "page"), repetitions = 1, contentUpdatedAt = now, srsUpdatedAt = 0L)

        val result = mergeCards(listOf(localCard), listOf(cloudCard))
        assertEquals(1, result.size)
        assertEquals("local-tie", result[0].pages[0])
        assertEquals(5, result[0].srsState.repetitions)
    }

    @Test
    fun `mergeCards full tie breaks to cloud`() {
        val localCard = makeCard("c3", pages = listOf("local-full-tie", "page"), repetitions = 3, contentUpdatedAt = now, srsUpdatedAt = now)
        val cloudCard = makeCard("c3", pages = listOf("cloud-full-tie", "page"), repetitions = 3, contentUpdatedAt = now, srsUpdatedAt = now)

        val result = mergeCards(listOf(localCard), listOf(cloudCard))
        assertEquals(1, result.size)
        assertEquals("cloud-full-tie", result[0].pages[0])
        assertEquals(3, result[0].srsState.repetitions)
    }

    @Test
    fun `mergeUserData basic combines modes cards and heatmap`() {
        val localCard = makeCard("c1", pages = listOf("local", "card"), repetitions = 4, contentUpdatedAt = now - 2000, srsUpdatedAt = now)
        val cloudCard = makeCard("c1", pages = listOf("cloud", "card"), repetitions = 1, contentUpdatedAt = now - 1000, srsUpdatedAt = 0L)

        val localSnapshot = StoreData(
            groups = listOf(makeDeck("g1", name = "Local deck", cards = listOf(localCard), updatedAt = now - 2000)),
            studyModes = listOf(StudyMode("custom-mode", name = "Custom", steps = listOf(WaitStep(ms = 100L)), isBuiltIn = false, updatedAt = now)),
            activityHeatmap = mapOf("2026-05-27" to 3),
        )
        val cloudSnapshot = StoreData(
            groups = listOf(makeDeck("g1", name = "Cloud deck", cards = listOf(cloudCard), updatedAt = now - 1000)),
            studyModes = listOf(StudyMode("classic", name = "Classic", steps = emptyList(), isBuiltIn = true, builtInSourceId = "classic", updatedAt = now)),
            activityHeatmap = mapOf("2026-05-27" to 1),
        )

        val merged = mergeUserData(localSnapshot, cloudSnapshot)
        assertTrue(merged.studyModes.any { it.id == "custom-mode" })
        assertEquals("cloud", merged.groups[0].cards[0].pages[0])
        assertEquals(4, merged.groups[0].cards[0].srsState.repetitions)
        assertEquals(3, merged.activityHeatmap["2026-05-27"])
    }

    @Test
    fun `tombstone propagation deleted card stays deleted`() {
        val deletedCard = makeCard("c3", pages = listOf("gone", "card"), contentUpdatedAt = now - 1000, deletedAt = now)
        val liveCard = makeCard("c3", pages = listOf("alive", "card"), repetitions = 1, contentUpdatedAt = now - 5000)

        val merged = mergeCards(listOf(deletedCard), listOf(liveCard))
        assertNotNull(merged[0].deletedAt)
    }

    @Test
    fun `edit newer than deletion revives card`() {
        val revivedCard = makeCard("c4", pages = listOf("revived", "card"), contentUpdatedAt = now, deletedAt = now - 10000)
        val merged = mergeCards(listOf(revivedCard), emptyList())
        assertNull(merged[0].deletedAt)
    }

    @Test
    fun `edit on one side newer than deletion on other side revives card`() {
        val localCard = makeCard("c5", pages = listOf("revived-local", "card"), contentUpdatedAt = now)
        val cloudCard = makeCard("c5", pages = listOf("cloud", "card"), contentUpdatedAt = now - 5000, deletedAt = now - 5000)

        val merged = mergeCards(listOf(localCard), listOf(cloudCard))
        assertNull(merged[0].deletedAt)
        assertEquals("revived-local", merged[0].pages[0])
    }

    @Test
    fun `mergeStudyModes combines both sides`() {
        val localModes = listOf(StudyMode("custom", name = "Custom Local", steps = listOf(ShowRatingsStep()), isBuiltIn = false, updatedAt = now))
        val cloudModes = listOf(StudyMode("classic", name = "Classic Cloud", steps = emptyList(), isBuiltIn = true, builtInSourceId = "classic", updatedAt = now - 5000))
        val merged = mergeStudyModes(localModes, cloudModes)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == "custom" })
        assertTrue(merged.any { it.id == "classic" })
    }

    @Test
    fun `mergeStudyModes deduplicates newer wins`() {
        val localMode = StudyMode("conflict", name = "Local Wins", steps = listOf(ShowRatingsStep()), isBuiltIn = false, updatedAt = now)
        val cloudMode = StudyMode("conflict", name = "Cloud Older", steps = emptyList(), isBuiltIn = false, updatedAt = now - 5000)
        val merged = mergeStudyModes(listOf(localMode), listOf(cloudMode))
        assertEquals(1, merged.size)
        assertEquals("Local Wins", merged[0].name)
    }

    @Test
    fun `mergeStudyModes tombstone propagates`() {
        val localTomb = StudyMode("tomb", name = "Deleted", steps = emptyList(), isBuiltIn = false, updatedAt = now, deletedAt = now)
        val cloudLive = StudyMode("tomb", name = "Alive", steps = emptyList(), isBuiltIn = false, updatedAt = now - 5000)
        val merged = mergeStudyModes(listOf(localTomb), listOf(cloudLive))
        assertEquals(1, merged.size)
        assertNotNull(merged[0].deletedAt)
    }

    @Test
    fun `mergeStudyModes built-in mode ignores tombstone`() {
        val localTomb = StudyMode(
            id = "classic",
            name = "Deleted classic",
            steps = emptyList(),
            isBuiltIn = true,
            builtInSourceId = "classic",
            updatedAt = now,
            deletedAt = now,
        )
        val cloudLive = StudyMode(
            id = "classic",
            name = "Cloud classic",
            steps = listOf(ShowRatingsStep()),
            isBuiltIn = true,
            builtInSourceId = "classic",
            updatedAt = now - 5000,
        )
        val merged = mergeStudyModes(listOf(localTomb), listOf(cloudLive))
        assertEquals(1, merged.size)
        assertNull(merged[0].deletedAt)
        assertEquals("Deleted classic", merged[0].name)
    }
}
