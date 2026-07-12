package com.raycoll.app.domain.selectors

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.srs.createNewSrsState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelectorsTest {
    private val now = System.currentTimeMillis()
    private val dayMs = 24L * 60 * 60 * 1000

    private fun card(id: String, state: Int = 0, nextReviewTimestamp: Long = 0L, reps: Int = 0, deletedAt: Long? = null) =
        Flashcard(id = id, pages = listOf("a", "b"), srsState = createNewSrsState().copy(state = state, nextReviewTimestamp = nextReviewTimestamp, repetitions = reps), deletedAt = deletedAt)

    @Test
    fun `filterCards all returns all live`() {
        val cards = listOf(card("c1"), card("c2", deletedAt = 0L))
        val result = filterCards(cards, CardFilter.all, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterCards new returns only state 0`() {
        val cards = listOf(card("c1", 0), card("c2", 2, now - 1000), card("c3", 2, now + 50000))
        val result = filterCards(cards, CardFilter.new, now)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `filterCards review returns only due`() {
        val cards = listOf(card("c1", 0), card("c2", 2, now - 1000), card("c3", 2, now + 50000))
        val result = filterCards(cards, CardFilter.review, now)
        assertEquals(1, result.size)
        assertEquals("c2", result[0].id)
    }

    @Test
    fun `filterCards new_review returns new plus due`() {
        val cards = listOf(card("c1", 0), card("c2", 2, now - 1000), card("c3", 2, now + 50000))
        val result = filterCards(cards, CardFilter.new_review, now)
        assertEquals(2, result.size)
    }

    @Test
    fun `computeCardStats categorizes correctly`() {
        val cards = listOf(
            card("c1", 0, reps = 0),
            card("c2", 1, reps = 1),
            card("c3", 3, reps = 2),
            card("c4", 2, reps = 2),
            card("c5", 2, reps = 5),
        )
        val stats = computeCardStats(cards)
        assertEquals(1, stats.newCount)
        assertEquals(2, stats.learning) // state 1 and 3
        assertEquals(1, stats.review) // state 2, reps=2
        assertEquals(1, stats.mastered) // state 2, reps=5
    }

    @Test
    fun `selectActiveGroups filters archived and deleted`() {
        val groups = listOf(
            FlashcardGroup(id = "g1", name = "Active", pageNames = listOf("a"), pageLanguages = listOf("en"), activeModeId = "c"),
            FlashcardGroup(id = "g2", name = "Archived", pageNames = listOf("a"), pageLanguages = listOf("en"), activeModeId = "c", archivedAt = now),
            FlashcardGroup(id = "g3", name = "Deleted", pageNames = listOf("a"), pageLanguages = listOf("en"), activeModeId = "c", deletedAt = now),
        )
        val active = selectActiveGroups(groups)
        assertEquals(1, active.size)
        assertEquals("g1", active[0].id)
    }

    @Test
    fun `getGroupProgress computes correctly`() {
        val cards = listOf(
            card("c1", reps = 0), card("c2", reps = 1), card("c3", reps = 2),
        )
        assertEquals(66, getGroupProgress(cards))
    }
}
