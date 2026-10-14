package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.ArchiveConstants
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.normalize.normalizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsTest {
    private val now = 1_700_000_000_000L
    private fun group() = FlashcardGroup(
        id = "g1",
        name = "Test",
        cards = listOf(newCard(listOf("hello", "world"), 0)),
        pageNames = listOf("Front", "Back"),
        pageLanguages = listOf("en-US", "pl-PL")
    )

    @Test
    fun `newCard gets id fresh SRS and content timestamp`() {
        val card = newCard(listOf("a", "b"), now)
        assertTrue(card.id.isNotBlank())
        assertEquals(listOf("a", "b"), card.pages)
        assertEquals(SrsState(), card.srsState)
        assertEquals(now, card.contentUpdatedAt)
    }

    @Test
    fun `reviewed changes SRS and review timestamp`() {
        val card = newCard(listOf("a", "b"), 0).reviewed(3, now)
        assertEquals(1, card.srsState.repetitions)
        assertEquals(now, card.srsUpdatedAt)
    }

    @Test
    fun `rerating from base replaces previous review instead of incrementing twice`() {
        val original = newCard(listOf("a", "b"), 0)
        val rerated = original.reviewed(1, now).reviewed(4, now, original.srsState)
        assertEquals(original.reviewed(4, now), rerated)
    }

    @Test
    fun `newGroup creates unique id and typed defaults`() {
        val deck = newGroup("  New Deck  ", listOf("en-US", "pl-PL"), listOf("Front", "Back"), emptyList(), now)
        assertTrue(deck.id.isNotBlank())
        assertEquals("New Deck", deck.name)
        assertEquals("classic", deck.activeModeId)
        assertEquals(StudyFilter.DEFAULT, deck.studyFilter)
        assertEquals(CardOrder.DEFAULT, deck.cardOrder)
        assertEquals(now, deck.updatedAt)
    }

    @Test
    fun `active page count is clamped to 2 through 5`() {
        assertEquals(5, group().withActivePageCount(10, now).activePageCount)
        assertEquals(2, group().withActivePageCount(0, now).activePageCount)
    }

    @Test
    fun `increasing active page count pads metadata and survives normalization`() {
        val deck = group().withActivePageCount(4, now)
        assertEquals(4, deck.pageNames.size)
        assertEquals(4, deck.pageLanguages.size)
        assertEquals(4, normalizeGroup(deck).activePageCount)
    }

    @Test
    fun `swapping pages swaps metadata and card contents`() {
        val deck = group().withPagesSwapped(0, 1, now)
        assertEquals(listOf("world", "hello"), deck.cards.single().pages)
        assertEquals(listOf("Back", "Front"), deck.pageNames)
        assertEquals(listOf("pl-PL", "en-US"), deck.pageLanguages)
        assertEquals(now, deck.updatedAt)
    }

    @Test
    fun `invalid page indices do not change the deck`() {
        val deck = group()
        assertEquals(deck, deck.withPagesSwapped(-1, 1, now))
        assertEquals(deck, deck.withPagesSwapped(0, 5, now))
    }

    @Test
    fun `swapping a newly added page pads short cards before moving their contents`() {
        val deck = group().withActivePageCount(3, now).withPagesSwapped(1, 2, now)
        assertEquals(listOf("hello", "", "world"), deck.cards.single().pages)
        assertEquals(listOf("Front", "Page 3", "Back"), deck.pageNames)
        assertEquals(listOf("en-US", "en-US", "pl-PL"), deck.pageLanguages)
    }

    @Test
    fun `swapping pages pads missing page languages instead of crashing`() {
        val deck = group().copy(pageLanguages = emptyList()).withPagesSwapped(0, 1, now)
        assertEquals(listOf("Back", "Front"), deck.pageNames)
        assertEquals(2, deck.pageLanguages.size)
    }

    @Test
    fun `archive expires at retention boundary`() {
        assertTrue(group().copy(archivedAt = now - ArchiveConstants.RETENTION_MS).isArchiveExpired(now))
        assertFalse(group().copy(archivedAt = now - ArchiveConstants.RETENTION_MS + 1).isArchiveExpired(now))
        assertFalse(group().isArchiveExpired(now))
        assertFalse(group().copy(archivedAt = 1, deletedAt = 2).isArchiveExpired(now))
    }
}
