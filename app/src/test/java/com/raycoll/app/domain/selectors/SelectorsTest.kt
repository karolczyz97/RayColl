package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.createNewSrsState
import com.raycoll.app.domain.normalize.DEFAULT_STUDY_FILTER
import com.raycoll.app.domain.normalize.DEFAULT_CARD_ORDER
import com.raycoll.app.core.LocalDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorsTest {
    private val now = 1700000000000L

    private fun makeCard(id: String, state: Int = 0, reps: Int = 0, nextReview: Long = 0, difficulty: Double = 5.0): Flashcard =
        Flashcard(id = id, pages = listOf("front", "back"), srsState = SrsState(difficulty = difficulty, repetitions = reps, state = state, nextReviewTimestamp = nextReview))

    private fun makeGroup(cards: List<Flashcard>): FlashcardGroup = FlashcardGroup(
        id = "g1", name = "Test", cards = cards,
        pageNames = listOf("Front", "Back"), pageLanguages = listOf("en-US", "pl-PL"),
        activePageCount = 2, studyFilter = DEFAULT_STUDY_FILTER, cardOrder = DEFAULT_CARD_ORDER,
    )

    @Test
    fun `filterCards all returns live cards`() {
        val cards = listOf(makeCard("c1"), makeCard("c2").copy(deletedAt = now))
        val result = filterCards(cards, "all", now)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterCards new returns state 0`() {
        val cards = listOf(makeCard("c1", state = 0), makeCard("c2", state = 2))
        val result = filterCards(cards, "new", now)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `filterCards review returns due cards`() {
        val cards = listOf(makeCard("c1", state = 2, nextReview = now - 1), makeCard("c2", state = 2, nextReview = now + 10000))
        val result = filterCards(cards, "review", now)
        assertEquals(1, result.size)
    }

    @Test
    fun `getGroupProgress 0 for empty`() {
        assertEquals(0, getGroupProgress(emptyList()))
    }

    @Test
    fun `getGroupProgress calculates learned percent`() {
        val cards = listOf(
            makeCard("c1", reps = 0),
            makeCard("c2", reps = 1),
            makeCard("c3", reps = 2),
            makeCard("c4", reps = 3),
        )
        assertEquals(75, getGroupProgress(cards))
    }

    @Test
    fun `computeCardStats counts categories`() {
        val cards = listOf(
            makeCard("c1", state = 0),
            makeCard("c2", state = 1),
            makeCard("c3", state = 2, reps = 2),
            makeCard("c4", state = 2, reps = 3),
            makeCard("c5", state = 3),
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
    fun `computeStreak counts consecutive days`() {
        val today = java.time.LocalDate.now()
        val todayKey = today.toString()
        val yesterdayKey = today.minusDays(1).toString()
        val heatmap = mapOf(todayKey to 5, yesterdayKey to 3)
        assertEquals(2, computeStreak(heatmap, System.currentTimeMillis()))
    }

    @Test
    fun `getVisiblePages slices to activePageCount`() {
        val card = makeCard("c1").copy(pages = listOf("a", "b", "c"))
        val group = makeGroup(emptyList()).copy(activePageCount = 2)
        assertEquals(listOf("a", "b"), getVisiblePages(card, group))
    }

    @Test
    fun `getVisiblePages pads to activePageCount`() {
        val card = makeCard("c1").copy(pages = listOf("a"))
        val group = makeGroup(emptyList()).copy(activePageCount = 2)
        assertEquals(listOf("a", ""), getVisiblePages(card, group))
    }

    @Test
    fun `browseFilter searches all pages`() {
        val cards = listOf(
            makeCard("c1").copy(pages = listOf("hello", "world")),
            makeCard("c2").copy(pages = listOf("goodbye", "world")),
        )
        val result = browseFilter(cards, "hello", null, now)
        assertEquals(1, result.cards.size)
        assertEquals("c1", result.cards[0].id)
    }

    @Test
    fun `archiveCountdown returns days until deletion`() {
        val group = makeGroup(emptyList()).copy(archivedAt = now - 7L * 86_400_000L)
        val days = archiveCountdown(group, now)
        assertEquals(7, days)
    }

    @Test
    fun `archiveCountdown returns -1 for non-archived`() {
        val group = makeGroup(emptyList())
        assertEquals(-1, archiveCountdown(group, now))
    }

    @Test
    fun `getGroupProgress rounds to nearest percent`() {
        // 2/3 = 66.67 → 67 (round), nie 66 (trunc)
        val cards = listOf(
            makeCard("c1", reps = 1),
            makeCard("c2", reps = 1),
            makeCard("c3", reps = 0),
        )
        assertEquals(67, getGroupProgress(cards))
    }

    @Test
    fun `random order can move the last card (Fisher-Yates covers every index)`() {
        val cards = listOf(makeCard("a"), makeCard("b"))
        // Pełny Fisher-Yates dla 2 kart MUSI potrafić wyprodukować [b, a] dla
        // przynajmniej jednego ziarna. Zepsuta pętla (gubiąca i=n-1) zawsze zwraca [a, b].
        val everSwapped = (0..50).any { seed ->
            orderCards(cards, "random", kotlin.random.Random(seed)).map { it.id } == listOf("b", "a")
        }
        assertTrue(everSwapped)
    }

    @Test
    fun `random order is a permutation of the input`() {
        val cards = (1..8).map { makeCard("c$it") }
        val shuffled = orderCards(cards, "random", kotlin.random.Random(123))
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
