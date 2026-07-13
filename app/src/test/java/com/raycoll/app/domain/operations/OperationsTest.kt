package com.raycoll.app.domain.operations

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.createNewSrsState
import com.raycoll.app.domain.normalize.DEFAULT_STUDY_FILTER
import com.raycoll.app.domain.normalize.DEFAULT_CARD_ORDER
import com.raycoll.app.domain.normalize.normalizeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsTest {
    private val now = 1700000000000L

    private fun makeGroup(id: String = "g1", cards: List<Flashcard> = emptyList()): FlashcardGroup = FlashcardGroup(
        id = id, name = "Test", cards = cards, activeModeId = "classic",
        studyFilter = DEFAULT_STUDY_FILTER, cardOrder = DEFAULT_CARD_ORDER,
        pageNames = listOf("Front", "Back"), pageLanguages = listOf("en-US", "pl-PL"),
        activePageCount = 2, updatedAt = 0,
    )

    private fun makeCard(id: String = "c1"): Flashcard = Flashcard(
        id = id, pages = listOf("hello", "world"), srsState = createNewSrsState(),
        contentUpdatedAt = 0, srsUpdatedAt = 0,
    )

    @Test
    fun `addCard adds card to group`() {
        val (groups, cardId) = addCard(listOf(makeGroup()), "g1", listOf("a", "b"), now)
        assertEquals(1, groups[0].cards.size)
        assertEquals(cardId, groups[0].cards[0].id)
        assertEquals(now, groups[0].cards[0].contentUpdatedAt)
    }

    @Test
    fun `deleteCard sets deletedAt (soft delete)`() {
        val group = makeGroup(cards = listOf(makeCard("c1")))
        val result = deleteCard(listOf(group), "g1", "c1", now)
        assertNotNull(result[0].cards[0].deletedAt)
        assertEquals(now, result[0].cards[0].deletedAt)
    }

    @Test
    fun `updateCard updates content and stamps contentUpdatedAt`() {
        val group = makeGroup(cards = listOf(makeCard("c1")))
        val updated = makeCard("c1").copy(pages = listOf("new", "pages"))
        val result = updateCard(listOf(group), "g1", updated, now)
        assertEquals(listOf("new", "pages"), result[0].cards[0].pages)
        assertEquals(now, result[0].cards[0].contentUpdatedAt)
    }

    @Test
    fun `reviewCard updates SRS state and srsUpdatedAt`() {
        val group = makeGroup(cards = listOf(makeCard("c1")))
        val result = reviewCard(listOf(group), "g1", "c1", 3, now)
        assertEquals(1, result[0].cards[0].srsState.repetitions)
        assertEquals(now, result[0].cards[0].srsUpdatedAt)
    }

    @Test
    fun `deleteGroup sets deletedAt and clears archivedAt`() {
        val group = makeGroup().copy(archivedAt = now - 1000)
        val result = deleteGroup(listOf(group), "g1", now)
        assertEquals(now, result[0].deletedAt)
        assertNull(result[0].archivedAt)
    }

    @Test
    fun `archiveGroup sets archivedAt`() {
        val result = archiveGroup(listOf(makeGroup()), "g1", now)
        assertEquals(now, result[0].archivedAt)
    }

    @Test
    fun `restoreGroup clears archivedAt`() {
        val group = makeGroup().copy(archivedAt = now - 1000)
        val result = restoreGroup(listOf(group), "g1", now)
        assertNull(result[0].archivedAt)
    }

    @Test
    fun `purgeExpiredArchives soft deletes expired`() {
        val retentionMs = 14L * 86_400_000L
        val group = makeGroup().copy(archivedAt = now - retentionMs - 1)
        val (result, changed) = purgeExpiredArchives(listOf(group), now, retentionMs)
        assertTrue(changed)
        assertNotNull(result[0].deletedAt)
    }

    @Test
    fun `purgeExpiredArchives does not touch non-archived`() {
        val retentionMs = 14L * 86_400_000L
        val group = makeGroup()
        val (result, changed) = purgeExpiredArchives(listOf(group), now, retentionMs)
        assertTrue(!changed)
        assertNull(result[0].deletedAt)
    }

    @Test
    fun `updateGroup preserves tombstoned cards not in edit`() {
        val liveCard = makeCard("c1")
        val deletedCard = makeCard("c2").copy(deletedAt = now - 1000)
        val group = makeGroup(cards = listOf(liveCard, deletedCard))
        val edited = group.copy(cards = listOf(liveCard))
        val result = updateGroup(listOf(group), edited, now)
        assertEquals(2, result[0].cards.size)
        assertTrue(result[0].cards.any { it.id == "c2" && it.deletedAt != null })
    }

    @Test
    fun `addGroup creates group with defaults`() {
        val (groups, id) = addGroup(emptyList(), "New Deck", listOf("en-US", "pl-PL"), listOf("Front", "Back"), now)
        assertEquals(1, groups.size)
        assertEquals("New Deck", groups[0].name)
        assertEquals("classic", groups[0].activeModeId)
        assertEquals(id, groups[0].id)
    }

    @Test
    fun `setVisiblePageCount clamps to 2-5`() {
        val group = makeGroup().copy(pageNames = listOf("A", "B", "C", "D", "E", "F"))
        val result = setVisiblePageCount(listOf(group), "g1", 10, now)
        assertEquals(5, result[0].activePageCount)
    }

    @Test
    fun `setVisiblePageCount increasing beyond stored pads metadata so it survives normalization`() {
        val group = makeGroup() // 2 strony
        val increased = setVisiblePageCount(listOf(group), "g1", 4, now)[0]
        assertEquals(4, increased.activePageCount)
        assertEquals(4, increased.pageNames.size)      // dopełnione do 4
        assertEquals(4, increased.pageLanguages.size)
        // normalizacja NIE cofa zwiększenia do 2 (storedPageCount urósł wraz z metadanymi)
        assertEquals(4, normalizeGroup(increased).activePageCount)
    }

    @Test
    fun `movePage swaps page content in cards`() {
        val card = makeCard("c1").copy(pages = listOf("hello", "world"))
        val group = makeGroup(cards = listOf(card))
        val result = movePage(listOf(group), "g1", 0, 1, now)
        assertEquals(listOf("world", "hello"), result[0].cards[0].pages)
        assertEquals(listOf("Back", "Front"), result[0].pageNames)
    }
}
