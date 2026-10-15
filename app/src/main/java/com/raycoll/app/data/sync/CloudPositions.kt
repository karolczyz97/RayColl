package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData

/** Dokument z chmury z zapisaną pozycją (null: dokument sprzed pola `position`). */
internal interface PositionedDoc {
    val docId: String
    val position: Long?
}

internal class Positioned<T>(override val docId: String, val value: T, override val position: Long?) : PositionedDoc

/** Talia z chmury (bez kart w [value]) razem z kartami. */
internal class FetchedDeck(
    override val docId: String,
    val value: FlashcardGroup,
    override val position: Long?,
    val cards: List<Positioned<Flashcard>>
) : PositionedDoc

/** Dane konta z chmury i zapisane pozycje dokumentów (klucze: [deckKey], [cardKey]). */
internal class FetchedData(
    val data: StoreData,
    val positions: Map<String, Long?>,
    val generations: Map<String, Pair<Long, String>> = emptyMap()
)

/** Kolejność wg `position`; dokumenty bez pozycji (starsze) na końcu, po id dokumentu. */
internal fun <T : PositionedDoc> List<T>.sortedByPosition(): List<T> =
    sortedWith(compareBy<T> { it.position ?: Long.MAX_VALUE }.thenBy { it.docId })

internal fun positionsOf(decks: List<FetchedDeck>): Map<String, Long?> = buildMap {
    for (deck in decks) {
        put(deckKey(deck.docId), deck.position)
        for (card in deck.cards) put(cardKey(deck.docId, card.docId), card.position)
    }
}

/**
 * Dokumenty, których zapisana pozycja nie jest ich miejscem na liście po odczycie
 * (brak pola, luka po usuniętym lub pominiętym dokumencie) — kolejny zapis poprawia je raz.
 */
internal fun stalePositionsOf(data: StoreData, stored: Map<String, Long?>): Set<String> = buildSet {
    data.groups.forEachIndexed { index, deck ->
        if (stored[deckKey(deck.id)] != index.toLong()) add(deckKey(deck.id))
        deck.cards.forEachIndexed { cardIndex, card ->
            if (stored[cardKey(deck.id, card.id)] != cardIndex.toLong()) add(cardKey(deck.id, card.id))
        }
    }
}
