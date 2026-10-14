package com.raycoll.app.domain.selectors

import com.raycoll.app.core.LocalDates
import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyFilter
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorsTest {

    @Test
    fun `archive expiring this evening says today rather than tomorrow`() {
        val day = LocalDate.of(2026, 10, 14)
        val zone = ZoneId.systemDefault()
        val expiresAt = day.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val now = day.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val group = makeGroup(emptyList()).copy(
            archivedAt =
                expiresAt - ArchiveConstants.RETENTION_MS
        )
        assertEquals(0, archiveDaysLeft(group, now))
        assertEquals(1, archiveDaysLeft(group, now - 86_400_000L))
    }
    private val now = 1700000000000L

    private fun makeCard(
        id: String,
        state: Int = 0,
        reps: Int = 0,
        nextReview: Long = 0,
        difficulty: Double = 5.0
    ): Flashcard = Flashcard(
        id = id,
        pages = listOf("front", "back"),
        srsState = SrsState(
            difficulty = difficulty,
            repetitions = reps,
            state = state,
            nextReviewTimestamp = nextReview
        )
    )

    private fun makeGroup(cards: List<Flashcard>): FlashcardGroup = FlashcardGroup(
        id = "g1",
        name = "Test",
        cards = cards,
        pageNames = listOf("Front", "Back"),
        pageLanguages = listOf("en-US", "pl-PL"),
        activePageCount = 2,
        studyFilter = StudyFilter.DEFAULT,
        cardOrder = CardOrder.DEFAULT
    )

    @Test
    fun `getDueCards all returns live cards`() {
        val cards = listOf(makeCard("c1"), makeCard("c2").copy(deletedAt = now))
        val result = getDueCards(cards, StudyFilter.ALL, CardOrder.DEFAULT, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `getDueCards new returns state 0`() {
        val cards = listOf(makeCard("c1", state = 0), makeCard("c2", state = 2))
        val result = getDueCards(cards, StudyFilter.NEW, CardOrder.DEFAULT, now)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `getDueCards review returns due cards`() {
        val cards = listOf(
            makeCard("c1", state = 2, nextReview = now - 1),
            makeCard(
                "c2",
                state = 2,
                nextReview =
                    now + 10000
            )
        )
        val result = getDueCards(cards, StudyFilter.REVIEW, CardOrder.DEFAULT, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `computeCardStats counts categories`() {
        val cards = listOf(
            makeCard("c1", state = 0),
            makeCard("c2", state = 1),
            makeCard("c3", state = 2, reps = 2),
            makeCard("c4", state = 2, reps = 3),
            makeCard("c5", state = 3)
        )
        val stats = computeCardStats(cards)
        assertEquals(5, stats.total)
        assertEquals(1, stats.newCount)
        assertEquals(2, stats.learning)
        assertEquals(1, stats.review)
        assertEquals(1, stats.mastered)
    }

    @Test
    fun `computeStreak 0 for empty heatmap`() {
        assertEquals(0, computeStreak(emptyMap(), now))
    }

    @Test
    fun `browse search does not inspect hidden pages`() {
        val cards = listOf(makeCard("c1").copy(pages = listOf("a", "b", "hidden")))
        assertTrue(browseFilter(cards, "hidden", null, 2).isEmpty())
        assertEquals(cards, browseFilter(cards, "hidden", null, 3))
    }

    @Test
    fun `browse safely handles a card shorter than the active page count`() {
        val cards = listOf(makeCard("c1").copy(pages = listOf("front")))
        assertEquals(cards, browseFilter(cards, "front", null, 2))
    }

    @Test
    fun `browseFilter searches all pages`() {
        val cards = listOf(
            makeCard("c1").copy(pages = listOf("hello", "world")),
            makeCard("c2").copy(pages = listOf("goodbye", "world"))
        )
        val result = browseFilter(cards, "hello", null, 2)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `archiveCountdown returns days until deletion`() {
        val group = makeGroup(emptyList()).copy(archivedAt = now - 7L * 86_400_000L)
        val days = archiveDaysLeft(group, now)
        assertEquals(7, days)
    }

    @Test
    fun `archiveCountdown returns null for non-archived`() {
        val group = makeGroup(emptyList())
        assertNull(archiveDaysLeft(group, now))
    }

    @Test
    fun `card statistics exclude tombstones`() {
        val cards = listOf(makeCard("live"), makeCard("deleted").copy(deletedAt = now))
        assertEquals(1, computeCardStats(cards).total)
        assertEquals(1, computeCardStats(cards).newCount)
    }

    @Test
    fun `random order can move the last card (Fisher-Yates covers every index)`() {
        val cards = listOf(makeCard("a"), makeCard("b"))
        // Pełny Fisher-Yates dla 2 kart MUSI potrafić wyprodukować [b, a] dla
        // przynajmniej jednego ziarna. Zepsuta pętla (gubiąca i=n-1) zawsze zwraca [a, b].
        val everSwapped = (0..50).any { seed ->
            orderCards(cards, CardOrder.RANDOM, kotlin.random.Random(seed)).map { it.id } == listOf("b", "a")
        }
        assertTrue(everSwapped)
    }

    @Test
    fun `random order is a permutation of the input`() {
        val cards = (1..8).map { makeCard("c$it") }
        val shuffled = orderCards(cards, CardOrder.RANDOM, kotlin.random.Random(123))
        assertEquals(cards.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        assertEquals(cards.size, shuffled.size)
    }

    @Test
    fun `computeStreak uses injected now not the system clock`() {
        // now = data z odległej przeszłości; klucze heatmapy budowane z tego samego now.
        // Gdyby streak startował od systemowego LocalDate.now(), pętla szukałaby dzisiejszych
        // kluczy (których w mapie nie ma) i zwróciłaby 0.
        val fixedNow = 1_579_000_000_000L // ~2020-01-14
        val todayKey = LocalDates.dayKey(fixedNow)
        val yesterdayKey = LocalDates.dayKey(fixedNow - 86_400_000L)
        val heatmap = mapOf(todayKey to 1, yesterdayKey to 1)
        assertEquals(2, computeStreak(heatmap, fixedNow))
    }
}
