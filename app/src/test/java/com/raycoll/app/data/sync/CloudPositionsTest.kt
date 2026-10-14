package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kolejność talii i kart z chmury: pole position, a dla starszych dokumentów — id dokumentu. */
class CloudPositionsTest {

    private fun card(id: String) = Flashcard(id = id, pages = listOf("f", "b"), srsState = SrsState())

    private fun deck(id: String, vararg cards: String) = FlashcardGroup(id = id, name = id, cards = cards.map(::card))

    private fun positioned(docId: String, position: Long?) = Positioned(docId, docId, position)

    @Test
    fun `sortedByPosition ustawia wg pozycji, a nie wg id dokumentu`() {
        val sorted = listOf(positioned("a", 2), positioned("b", 0), positioned("c", 1)).sortedByPosition()
        assertEquals(listOf("b", "c", "a"), sorted.map { it.docId })
    }

    @Test
    fun `dokumenty bez pozycji idą na koniec, po id dokumentu`() {
        val sorted = listOf(
            positioned("z", null),
            positioned("m", 1),
            positioned("b", null),
            positioned("x", 0)
        ).sortedByPosition()
        assertEquals(listOf("x", "m", "b", "z"), sorted.map { it.docId })
    }

    @Test
    fun `stalePositionsOf nie zgłasza dokumentów z pozycją równą miejscu na liście`() {
        val data = StoreData(groups = listOf(deck("d1", "c1", "c2"), deck("d2", "c3")))
        val stored = mapOf<String, Long?>(
            deckKey("d1") to 0,
            deckKey("d2") to 1,
            cardKey("d1", "c1") to 0,
            cardKey("d1", "c2") to 1,
            cardKey("d2", "c3") to 0
        )
        assertTrue(stalePositionsOf(data, stored).isEmpty())
    }

    @Test
    fun `stalePositionsOf zgłasza brak pozycji i luki`() {
        val data = StoreData(groups = listOf(deck("d1", "c1", "c2", "c3")))
        val stored = mapOf<String, Long?>(
            deckKey("d1") to null,
            cardKey("d1", "c1") to 0,
            cardKey("d1", "c2") to 4, // luka po usuniętym dokumencie
            cardKey("d1", "c3") to null
        )
        assertEquals(
            setOf(deckKey("d1"), cardKey("d1", "c2"), cardKey("d1", "c3")),
            stalePositionsOf(data, stored)
        )
    }

    @Test
    fun `positionsOf zbiera pozycje talii i kart pod kluczami diffu`() {
        val cards = listOf(Positioned("c1", card("c1"), 3), Positioned("c2", card("c2"), null))
        val decks = listOf(FetchedDeck("d1", deck("d1"), 0, cards))
        assertEquals(
            mapOf<String, Long?>(deckKey("d1") to 0, cardKey("d1", "c1") to 3, cardKey("d1", "c2") to null),
            positionsOf(decks)
        )
    }
}
