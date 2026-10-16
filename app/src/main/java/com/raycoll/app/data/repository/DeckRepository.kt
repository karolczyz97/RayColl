package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import com.raycoll.app.core.LocalDates
import com.raycoll.app.data.account.CurrentAccount
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.FieldOp
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.firestore.WriteOp
import com.raycoll.app.data.remote.firestore.FirestoreSchema as Schema
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyFilter
import com.raycoll.app.domain.model.defaultPageName
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DeckRepository @Inject constructor(
    private val store: DocumentStore,
    private val account: CurrentAccount,
    private val clock: Clock
) {
    private val reviews = Mutex()
    private val metadata = Mutex()
    private fun paths() = UserPaths(checkNotNull(account.uid.value))

    private fun decksWithCards(predicate: (FlashcardGroup) -> Boolean): Flow<List<FlashcardGroup>> =
        account.uid.filterNotNull().flatMapLatest { uid ->
            val paths = UserPaths(uid)
            store.observe(paths.decks()).flatMapLatest { result ->
                val decks = Schema.sorted(result.docs).mapNotNull(Schema::readDeck)
                    .filter { it.deletedAt == null && predicate(it) }
                if (decks.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        decks.map { deck ->
                            store.observe(paths.cards(deck.id)).map { cards ->
                                deck.copy(
                                    cards = Schema.sorted(cards.docs).mapNotNull(Schema::readCard).filter {
                                        it.deletedAt ==
                                            null
                                    }
                                )
                            }
                        }
                    ) { it.toList() }
                }
            }
        }

    fun activeDecks() = decksWithCards { it.archivedAt == null }
    fun archivedDecks() = decksWithCards {
        it.archivedAt != null
    }.map { decks -> decks.sortedByDescending { it.archivedAt } }
    fun deck(deckId: String): Flow<FlashcardGroup?> = decksWithCards { it.id == deckId }.map { it.firstOrNull() }
    fun activityHeatmap(): Flow<Map<String, Int>> = account.uid.filterNotNull().flatMapLatest { uid ->
        store.observe(UserPaths(uid).activity()).map { docs ->
            docs.docs.mapNotNull { doc ->
                runCatching { doc.id to Schema.activityCountFromMap(doc.id, doc.data) }.getOrNull()
            }.toMap()
        }
    }

    suspend fun getDeck(deckId: String): FlashcardGroup? {
        // Ocena najpierw czyta kartę, dopiero potem zapisuje. Czekamy, aż wcześniejsze oceny oddadzą
        // zapis Firestore, które wykonuje operacje po kolei, więc odczyt poniżej widzi ich wynik.
        reviews.withLock { }
        val paths = paths()
        val deck =
            store.cachedDoc(paths.deck(deckId))?.let(Schema::readDeck)?.takeIf { it.deletedAt == null } ?: return null
        return deck.copy(
            cards = Schema.sorted(store.cached(paths.cards(deckId))).mapNotNull(Schema::readCard).filter {
                it.deletedAt ==
                    null
            }
        )
    }

    suspend fun addDeckWithCards(
        name: String,
        languages: List<String>,
        pageNames: List<String>,
        cardPages: List<List<String>>
    ): String {
        val paths = paths()
        val now = clock.now()
        val deck = newGroup(name, languages, pageNames, cardPages.map { newCard(it, now) }, now)
        val position = nextPosition(paths.decks())
        store.writeAll(
            listOf(WriteOp.Set(paths.deck(deck.id), Schema.deckToMap(deck, position))) +
                deck.cards.mapIndexed { index, card ->
                    WriteOp.Set(paths.card(deck.id, card.id), Schema.cardToMap(card, index))
                }
        )
        return deck.id
    }

    private suspend fun nextPosition(collection: String): Int =
        ((store.cached(collection).mapNotNull { Schema.positionFromMap(it.data) }.maxOrNull() ?: -1L) + 1).toInt()

    private suspend fun updateDeck(deckId: String, fields: Map<String, Any?>) {
        val path = paths().deck(deckId)
        val current = store.cachedDoc(path) ?: return
        if (current.data["deletedAt"] != null || fields.all { current.data[it.key] == it.value }) return
        store.write(WriteOp.Update(path, fields + ("updatedAt" to clock.now())))
    }

    suspend fun renameDeck(deckId: String, name: String) = updateDeck(deckId, mapOf("name" to name.trim()))
    suspend fun setStudyFilter(deckId: String, filter: StudyFilter) =
        updateDeck(deckId, mapOf("studyFilter" to filter.key))
    suspend fun setCardOrder(deckId: String, order: CardOrder) = updateDeck(deckId, mapOf("cardOrder" to order.key))
    suspend fun setActiveStudyMode(deckId: String, modeId: String) = updateDeck(deckId, mapOf("activeModeId" to modeId))
    suspend fun markStudied(deckId: String) = updateDeck(deckId, mapOf("updatedAt" to clock.now()))

    suspend fun setPageName(deckId: String, pageIndex: Int, name: String) = editPages(deckId) { deck ->
        if (pageIndex in
            deck.pageNames.indices
        ) {
            deck.copy(
                pageNames = deck.pageNames.mapIndexed { i, value ->
                    if (i ==
                        pageIndex
                    ) {
                        name.trim()
                    } else {
                        value
                    }
                }
            )
        } else {
            deck
        }
    }
    suspend fun setPageLanguage(deckId: String, pageIndex: Int, language: String) = editPages(deckId) { deck ->
        if (pageIndex in
            deck.pageLanguages.indices
        ) {
            deck.copy(
                pageLanguages = deck.pageLanguages.mapIndexed { i, value ->
                    if (i ==
                        pageIndex
                    ) {
                        language
                    } else {
                        value
                    }
                }
            )
        } else {
            deck
        }
    }
    suspend fun setActivePageCount(deckId: String, count: Int, defaultName: (Int) -> String = ::defaultPageName) =
        editPages(deckId) { it.withActivePageCount(count, clock.now(), defaultName) }

    private fun pageFields(deck: FlashcardGroup): Map<String, Any?> = mapOf(
        "pageNames" to deck.pageNames,
        "pageLanguages" to deck.pageLanguages,
        "activePageCount" to deck.activePageCount
    )
    private suspend fun editPages(deckId: String, edit: (FlashcardGroup) -> FlashcardGroup) = metadata.withLock {
        val deck = getDeck(deckId) ?: return@withLock
        updateDeck(deckId, pageFields(edit(deck)))
    }
    suspend fun swapPages(deckId: String, from: Int, to: Int) = metadata.withLock {
        val deck = getDeck(deckId) ?: return@withLock
        if (from == to || from !in deck.pageNames.indices || to !in deck.pageNames.indices) return@withLock
        val swapped = deck.withPagesSwapped(from, to, clock.now())
        val paths = paths()
        store.writeAll(
            listOf(WriteOp.Update(paths.deck(deckId), pageFields(swapped) + ("updatedAt" to clock.now()))) +
                swapped.cards.map {
                    WriteOp.Update(
                        paths.card(deckId, it.id),
                        mapOf(
                            "pages" to it.pages,
                            "contentUpdatedAt" to clock.now()
                        )
                    )
                }
        )
    }
    suspend fun deleteDeck(deckId: String) = updateDeck(deckId, mapOf("deletedAt" to clock.now()))
    suspend fun archiveDeck(deckId: String) = updateDeck(deckId, mapOf("archivedAt" to clock.now()))
    suspend fun restoreDeck(deckId: String) = updateDeck(deckId, mapOf("archivedAt" to null))

    suspend fun addCard(deckId: String, pages: List<String>) {
        val card = newCard(pages, clock.now())
        val paths = paths()
        store.write(WriteOp.Set(paths.card(deckId, card.id), Schema.cardToMap(card, nextPosition(paths.cards(deckId)))))
    }
    private suspend fun cardPath(cardId: String): String? {
        val paths = paths()
        for (deck in store.cached(paths.decks())) {
            val path = paths.card(deck.id, cardId)
            if (store.cachedDoc(path) != null) return path
        }
        return null
    }
    suspend fun updateCardPages(cardId: String, visiblePages: List<String>): Boolean = metadata.withLock {
        val path = cardPath(cardId) ?: return@withLock false
        val card =
            store.cachedDoc(path)?.let(Schema::readCard)?.takeIf { it.deletedAt == null } ?: return@withLock false
        store.write(
            WriteOp.Update(
                path,
                mapOf(
                    "pages" to visiblePages + card.pages.drop(visiblePages.size),
                    "contentUpdatedAt" to clock.now()
                )
            )
        )
        true
    }
    suspend fun deleteCard(cardId: String) {
        val path = cardPath(cardId) ?: return
        if (store.cachedDoc(path)?.data?.get("deletedAt") != null) return
        store.write(WriteOp.Update(path, mapOf("deletedAt" to clock.now())))
    }

    /** Cofnięcie usunięcia karty (zdjęcie znacznika usunięcia). */
    suspend fun restoreCard(cardId: String) {
        val path = cardPath(cardId) ?: return
        if (store.cachedDoc(path)?.data?.get("deletedAt") == null) return
        store.write(WriteOp.Update(path, mapOf("deletedAt" to null)))
    }

    suspend fun reviewCard(deckId: String, cardId: String, rating: Int, baseState: SrsState? = null) =
        reviews.withLock {
            val paths = paths()
            val path = paths.card(deckId, cardId)
            val card = store.cachedDoc(path)?.let(Schema::readCard)?.takeIf { it.deletedAt == null } ?: return@withLock
            val now = clock.now()
            val reviewed = card.reviewed(rating, now, baseState ?: card.srsState)
            store.write(
                WriteOp.Update(
                    path,
                    mapOf(
                        "srsState" to Schema.cardToMap(reviewed).getValue("srsState"),
                        "srsUpdatedAt" to now
                    )
                )
            )
            if (baseState ==
                null
            ) {
                store.write(
                    WriteOp.Set(
                        paths.activityDay(LocalDates.dayKey(now)),
                        mapOf("count" to FieldOp.Increment(1), "updatedAt" to FieldOp.ServerTime),
                        merge = true
                    )
                )
            }
        }
}
