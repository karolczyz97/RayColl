package com.raycoll.app.data.repository

import androidx.room.withTransaction
import com.raycoll.app.core.Clock
import com.raycoll.app.core.LocalDates
import com.raycoll.app.data.local.db.CardEntity
import com.raycoll.app.data.local.db.DeckEntity
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.local.mappers.encodeStringList
import com.raycoll.app.data.local.mappers.toDomain
import com.raycoll.app.data.local.mappers.toEntity
import com.raycoll.app.data.local.mappers.toHeatmap
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.operations.isArchiveExpired
import com.raycoll.app.domain.operations.newCard
import com.raycoll.app.domain.operations.newGroup
import com.raycoll.app.domain.operations.reviewed
import com.raycoll.app.domain.operations.withActivePageCount
import com.raycoll.app.domain.operations.withPagesSwapped
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Talie, karty i dzienna aktywność w aktywnej przestrzeni danych. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DeckRepository @Inject constructor(
    private val db: RayCollDatabase,
    private val clock: Clock,
    private val namespaceManager: NamespaceManager,
    private val dataMutations: DataMutations
) {
    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val activityDao = db.activityDao()

    private val ns: String get() = namespaceManager.current()

    // --- odczyt: strumienie śledzą aktywne konto ---

    fun activeDecks(): Flow<List<FlashcardGroup>> = decksWithCards(deckDao::activeDecks)

    fun archivedDecks(): Flow<List<FlashcardGroup>> = decksWithCards(deckDao::archivedDecks)

    /**
     * Talia z kartami do pokazania; null, gdy jej nie ma albo jest usunięta (tombstone
     * zna tylko synchronizacja i kopia zapasowa — [LocalStore.snapshot]). Zarchiwizowana jest.
     */
    fun deck(deckId: String): Flow<FlashcardGroup?> = namespaceManager.active.flatMapLatest { ns ->
        combine(deckDao.liveDeck(ns, deckId), cardDao.liveCardsOf(ns, deckId)) { deck, cards -> deck?.toDomain(cards) }
    }

    fun activityHeatmap(): Flow<Map<String, Int>> = namespaceManager.active.flatMapLatest { ns ->
        activityDao.heatmap(ns).map { it.toHeatmap() }
    }

    /** Jak [deck], jednorazowo: usunięta talia = null. */
    suspend fun getDeck(deckId: String): FlashcardGroup? {
        val ns = ns
        val deck = deckDao.getLiveDeck(ns, deckId) ?: return null
        return deck.toDomain(cardDao.getLiveCardsOf(ns, deckId))
    }

    /**
     * Karty idą osobnym strumieniem, bo zapytanie na samych taliach nie emituje
     * przy dodaniu/edycji/usunięciu karty.
     */
    private fun decksWithCards(decks: (String) -> Flow<List<DeckEntity>>): Flow<List<FlashcardGroup>> =
        namespaceManager.active.flatMapLatest { ns ->
            combine(decks(ns), cardDao.liveCards(ns), ::withCards)
        }

    // --- talie ---

    /** Nowa talia z kartami (import) — zapis krytyczny, wysyłany od razu. */
    suspend fun addDeckWithCards(
        name: String,
        languages: List<String>,
        pageNames: List<String>,
        cardPages: List<List<String>>
    ): String {
        val now = clock.now()
        val deck = newGroup(name, languages, pageNames, cardPages.map { newCard(it, now) }, now)
        val ns = ns
        db.withTransaction {
            deckDao.upsertDeck(deck.toEntity(ns))
            cardDao.upsertCards(deck.cards.map { it.toEntity(ns, deck.id) })
        }
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
        return deck.id
    }

    // --- ustawienia talii: każda zmiana dotyka tylko swoich kolumn (bez „pobierz → upsert całego wiersza”) ---

    suspend fun renameDeck(deckId: String, name: String) = updateDeck { ns, now ->
        deckDao.updateName(ns, deckId, name.trim(), now)
    }

    suspend fun setStudyFilter(deckId: String, filter: StudyFilter) = updateDeck { ns, now ->
        deckDao.updateStudyFilter(ns, deckId, filter.key, now)
    }

    suspend fun setCardOrder(deckId: String, order: CardOrder) = updateDeck { ns, now ->
        deckDao.updateCardOrder(ns, deckId, order.key, now)
    }

    suspend fun setActiveStudyMode(deckId: String, modeId: String) = updateDeck { ns, now ->
        deckDao.updateActiveMode(ns, deckId, modeId, now)
    }

    suspend fun setPageName(deckId: String, pageIndex: Int, name: String) = editPageMetadata(deckId) { deck, _ ->
        deck.copy(pageNames = deck.pageNames.mapIndexed { i, old -> if (i == pageIndex) name else old })
    }

    suspend fun setPageLanguage(deckId: String, pageIndex: Int, language: String) =
        editPageMetadata(deckId) { deck, _ ->
            deck.copy(pageLanguages = deck.pageLanguages.mapIndexed { i, old -> if (i == pageIndex) language else old })
        }

    suspend fun setActivePageCount(deckId: String, count: Int) = editPageMetadata(deckId) { deck, now ->
        deck.withActivePageCount(count, now)
    }

    private suspend fun updateDeck(write: suspend (ns: String, now: Long) -> Int) {
        if (write(ns, clock.now()) > 0) dataMutations.signal(CloudWriteMode.DEBOUNCED)
    }

    /**
     * Nazwy/języki/liczba stron (listy JSON): odczyt i zapis tych kolumn w jednej transakcji,
     * więc dwie szybkie zmiany różnych stron nie gubią się nawzajem; reszta wiersza bez zmian.
     */
    private suspend fun editPageMetadata(deckId: String, edit: (FlashcardGroup, Long) -> FlashcardGroup) {
        val ns = ns
        val now = clock.now()
        val updated = db.withTransaction {
            val deck = deckDao.getLiveDeck(ns, deckId)?.toDomain(emptyList()) ?: return@withTransaction 0
            val edited = edit(deck, now)
            deckDao.updatePageMetadata(
                ns,
                deckId,
                encodeStringList(edited.pageNames),
                encodeStringList(edited.pageLanguages),
                edited.activePageCount,
                now
            )
        }
        if (updated > 0) dataMutations.signal(CloudWriteMode.DEBOUNCED)
    }

    /**
     * Zamiana stron miejscami: metadane talii i treść każdej karty w jednej transakcji
     * (operacja wielokolumnowa — razem albo wcale); updatedAt talii i contentUpdatedAt kart = teraz.
     */
    suspend fun swapPages(deckId: String, from: Int, to: Int) {
        val ns = ns
        val now = clock.now()
        val swappedAny = db.withTransaction {
            val deck = getDeck(deckId) ?: return@withTransaction false
            if (from !in deck.pageNames.indices || to !in deck.pageNames.indices) return@withTransaction false
            val swapped = deck.withPagesSwapped(from, to, now)
            deckDao.updatePageMetadata(
                ns,
                deckId,
                encodeStringList(swapped.pageNames),
                encodeStringList(swapped.pageLanguages),
                swapped.activePageCount,
                now
            )
            cardDao.upsertCards(swapped.cards.map { it.copy(contentUpdatedAt = now).toEntity(ns, deckId) })
            true
        }
        if (swappedAny) dataMutations.signal(CloudWriteMode.DEBOUNCED)
    }

    suspend fun deleteDeck(deckId: String) {
        deckDao.softDeleteDeck(ns, deckId, clock.now())
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    suspend fun archiveDeck(deckId: String) {
        deckDao.archiveDeck(ns, deckId, clock.now())
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    suspend fun restoreDeck(deckId: String) {
        deckDao.restoreDeck(ns, deckId, clock.now())
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    /** Trwałe usunięcie talii zarchiwizowanych dłużej niż okres przechowywania. */
    suspend fun purgeExpiredArchives() {
        val ns = ns
        val now = clock.now()
        val expired = deckDao.allDecks(ns).map { it.toDomain(emptyList()) }.filter { it.isArchiveExpired(now) }
        if (expired.isEmpty()) return
        db.withTransaction { expired.forEach { deckDao.softDeleteDeck(ns, it.id, now) } }
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    // --- karty ---

    suspend fun addCard(deckId: String, pages: List<String>) {
        cardDao.upsertCard(newCard(pages, clock.now()).toEntity(ns, deckId))
        dataMutations.signal(CloudWriteMode.DEBOUNCED)
    }

    /**
     * Nowa treść widocznych stron karty: zmienia tylko strony i [Flashcard.contentUpdatedAt],
     * więc równoległa ocena (SRS) ani ukryte strony nie są nadpisywane. False: karty już nie ma.
     */
    suspend fun updateCardPages(cardId: String, visiblePages: List<String>): Boolean {
        val ns = ns
        val now = clock.now()
        val updated = db.withTransaction {
            val current = cardDao.getCard(ns, cardId)?.takeIf { it.deletedAt == null }?.toDomain()
                ?: return@withTransaction false
            val pages = visiblePages + current.pages.drop(visiblePages.size)
            cardDao.updateCardContent(ns, cardId, encodeStringList(pages), now) > 0
        }
        if (updated) dataMutations.signal(CloudWriteMode.DEBOUNCED)
        return updated
    }

    suspend fun deleteCard(cardId: String) {
        cardDao.softDeleteCard(ns, cardId, clock.now())
        dataMutations.signal(CloudWriteMode.IMMEDIATE)
    }

    /**
     * Ocena karty w sesji. [baseState] != null: ponowna ocena liczona od stanu sprzed
     * pierwszej i bez drugiego wpisu w aktywności dnia.
     */
    suspend fun reviewCard(
        deckId: String,
        cardId: String,
        rating: Int,
        baseState: SrsState? = null,
        namespace: String = namespaceManager.current()
    ) {
        val ns = namespace
        val now = clock.now()
        db.withTransaction {
            val card =
                cardDao.getCard(ns, cardId)?.takeIf { it.deletedAt == null }?.toDomain() ?: return@withTransaction
            cardDao.upsertCard(card.reviewed(rating, now, baseState ?: card.srsState).toEntity(ns, deckId))
            if (baseState == null) activityDao.incrementActivity(ns, LocalDates.dayKey(now))
        }
        dataMutations.signal(CloudWriteMode.STUDY)
    }
}

internal fun withCards(decks: List<DeckEntity>, cards: List<CardEntity>): List<FlashcardGroup> {
    val cardsByDeck = cards.groupBy { it.deckId }
    return decks.map { it.toDomain(cardsByDeck[it.id].orEmpty()) }
}
