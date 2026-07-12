package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.srs.createNewSrsState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OperationsTest {
    private val now = System.currentTimeMillis()
    private val dayMs = 14L * 24 * 60 * 60 * 1000

    @Test
    fun `addFlashcard adds card with new SRS state`() {
        val (groups, cardId) = addFlashcard(listOf(FlashcardGroup(id = "g1", name = "Test", activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))), "g1", listOf("hello", "world"), now)
        assertEquals(1, groups[0].cards.size)
        assertTrue(cardId.isNotEmpty())
        assertEquals(0, groups[0].cards[0].srsState.state)
    }

    @Test
    fun `reviewFlashcard bumps heatmap`() {
        val card = Flashcard(id = "c1", pages = listOf("a"), srsState = createNewSrsState())
        val group = FlashcardGroup(id = "g1", name = "Test", cards = listOf(card), activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val (next, heatmap, _) = reviewFlashcard(listOf(group), "g1", "c1", 3, mapOf("today" to 1), "today", now)
        assertEquals(2, heatmap["today"])
        assertEquals(1, next[0].cards[0].srsState.repetitions)
    }

    @Test
    fun `deleteFlashcard sets deletedAt`() {
        val card = Flashcard(id = "c1", pages = listOf("a"))
        val group = FlashcardGroup(id = "g1", name = "Test", cards = listOf(card), activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val result = deleteFlashcard(listOf(group), "g1", "c1", now)
        assertNotNull(result[0].cards[0].deletedAt)
    }

    @Test
    fun `archiveGroup sets archivedAt`() {
        val group = FlashcardGroup(id = "g1", name = "Test", activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val result = archiveGroup(listOf(group), "g1", now)
        assertEquals(now, result[0].archivedAt)
    }

    @Test
    fun `restoreGroup clears archivedAt`() {
        val group = FlashcardGroup(id = "g1", name = "Test", archivedAt = now - 10000, activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val result = restoreGroup(listOf(group), "g1", now)
        assertNull(result[0].archivedAt)
    }

    @Test
    fun `purgeExpiredArchives deletes old archives`() {
        val group = FlashcardGroup(id = "g1", name = "Old", archivedAt = now - 2 * dayMs, activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val (result, changed) = purgeExpiredArchives(listOf(group), now, dayMs)
        assertTrue(changed)
        assertNotNull(result[0].deletedAt)
    }

    @Test
    fun `purgeExpiredArchives keeps recent archives`() {
        val group = FlashcardGroup(id = "g1", name = "New", archivedAt = now, activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val (result, changed) = purgeExpiredArchives(listOf(group), now, dayMs)
        assertFalse(changed)
    }

    @Test
    fun `addGroup creates group with id`() {
        val (groups, id) = addGroup(emptyList(), "Test Deck", listOf("en-US", "pl-PL"), listOf("Front", "Back"), now)
        assertEquals(1, groups.size)
        assertTrue(id.isNotEmpty())
        assertEquals("Test Deck", groups[0].name)
    }

    @Test
    fun `updateGroup preserves tombstoned cards not in edit`() {
        val deleted = Flashcard(id = "c_del", pages = listOf("a"), deletedAt = now)
        val group = FlashcardGroup(id = "g1", name = "Test", cards = listOf(deleted), activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val updated = group.copy(name = "Updated", cards = emptyList())
        val result = updateGroup(listOf(group), updated, now)
        assertEquals(1, result[0].cards.size)
        assertEquals("c_del", result[0].cards[0].id)
    }

    @Test
    fun `reviewFlashcardAgain does not bump heatmap`() {
        val baseSrs = createNewSrsState()
        val card = Flashcard(id = "c1", pages = listOf("a"), srsState = createNewSrsState().copy(repetitions = 5))
        val group = FlashcardGroup(id = "g1", name = "Test", cards = listOf(card), activeModeId = "c", pageNames = listOf("a"), pageLanguages = listOf("en"))
        val result = reviewFlashcardAgain(listOf(group), "g1", "c1", 3, baseSrs, now)
        assertEquals(1, result[0].cards[0].srsState.repetitions)
    }
}
