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
import com.raycoll.app.domain.model.isLive
import com.raycoll.app.domain.operations.newCard
import com.raycoll.app.domain.operations.newGroup
import com.raycoll.app.domain.operations.reviewed
import com.raycoll.app.domain.operations.withActivePageCount
import com.raycoll.app.domain.operations.withPageLanguage
import com.raycoll.app.domain.operations.withPageMoved
import com.raycoll.app.domain.operations.withPageName
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
                    .filter { it.isLive && predicate(it) }
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
            docs.docs.mapNotNull(Schema::readActivity).toMap()
        }
    }

    suspend fun getDeck(deckId: String): FlashcardGroup? {
        // Ocena najpierw czyta kartę, dopiero potem zapisuje. Czekamy, aż wcześniejsze oceny oddadzą
        // zapis Firestore, które wykonuje operacje po kolei, więc odczyt poniżej widzi ich wynik.
        reviews.withLock { }
        val paths = paths()
        val deck =
            store.cachedDoc(paths.deck(deckId))?.let(Schema::readDeck)?.takeIf { it.isLive } ?: return null
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
        if (current.data[Schema.FIELD_DELETED_AT] != null || fields.all { current.data[it.key] == it.value }) return
        store.write(WriteOp.Update(path, fields + (Schema.FIELD_UPDATED_AT to clock.now())))
    }

    suspend fun renameDeck(deckId: String, name: String) = updateDeck(deckId, mapOf("name" to name.trim()))
    suspend fun setStudyFilter(deckId: String, filter: StudyFilter) =
        updateDeck(deckId, mapOf("studyFilter" to filter.key))
    suspend fun setCardOrder(deckId: String, order: CardOrder) = updateDeck(deckId, mapOf("cardOrder" to order.key))
    suspend fun setActiveStudyMode(deckId: String, modeId: String) = updateDeck(deckId, mapOf("activeModeId" to modeId))
    suspend fun markStudied(deckId: String) = updateDeck(deckId, mapOf(Schema.FIELD_UPDATED_AT to clock.now()))

    suspend fun reorderDecks(deckIds: List<String>) = metadata.withLock {
        val paths = paths()
        val active = Schema.sorted(store.cached(paths.decks())).filter { doc ->
            Schema.readDeck(doc)?.let { it.isLive && it.archivedAt == null } == true
        }
        val byId = active.associateBy { it.id }
        val requested = deckIds.distinct().mapNotNull(byId::get)
        val requestedIds = requested.mapTo(mutableSetOf()) { it.id }
        // Talie dodane podczas przeciągania zostają na końcu; usunięte i zarchiwizowane pomijamy.
        val reordered = requested + active.filter { it.id !in requestedIds }
        if (reordered == active) return@withLock
        val now = clock.now()
        store.writeAll(
            reordered.mapIndexedNotNull { position, doc ->
                if (Schema.positionFromMap(doc.data) == position.toLong()) {
                    null
                } else {
                    WriteOp.Update(
                        paths.deck(doc.id),
                        mapOf("position" to position, Schema.FIELD_UPDATED_AT to now)
                    )
                }
            }
        )
    }

    suspend fun setPageName(deckId: String, pageIndex: Int, name: String) =
        editPages(deckId) { it.withPageName(pageIndex, name) }
    suspend fun setPageLanguage(deckId: String, pageIndex: Int, language: String) =
        editPages(deckId) { it.withPageLanguage(pageIndex, language) }
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
    suspend fun movePage(deckId: String, from: Int, to: Int) = metadata.withLock {
        val deck = getDeck(deckId) ?: return@withLock
        if (from == to || from !in deck.pageNames.indices || to !in deck.pageNames.indices) return@withLock
        val now = clock.now()
        val moved = deck.withPageMoved(from, to, now)
        val paths = paths()
        store.writeAll(
            listOf(WriteOp.Update(paths.deck(deckId), pageFields(moved) + (Schema.FIELD_UPDATED_AT to now))) +
                moved.cards.map {
                    WriteOp.Update(paths.card(deckId, it.id), mapOf("pages" to it.pages, "contentUpdatedAt" to now))
                }
        )
    }
    suspend fun deleteDeck(deckId: String) = updateDeck(deckId, mapOf(Schema.FIELD_DELETED_AT to clock.now()))
    suspend fun archiveDeck(deckId: String) = updateDeck(deckId, mapOf(Schema.FIELD_ARCHIVED_AT to clock.now()))
    suspend fun restoreDeck(deckId: String) = updateDeck(deckId, mapOf(Schema.FIELD_ARCHIVED_AT to null))

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
            store.cachedDoc(path)?.let(Schema::readCard)?.takeIf { it.isLive } ?: return@withLock false
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
        if (store.cachedDoc(path)?.data?.get(Schema.FIELD_DELETED_AT) != null) return
        store.write(WriteOp.Update(path, mapOf(Schema.FIELD_DELETED_AT to clock.now())))
    }

    /** Cofnięcie usunięcia karty (zdjęcie znacznika usunięcia). */
    suspend fun restoreCard(cardId: String) {
        val path = cardPath(cardId) ?: return
        if (store.cachedDoc(path)?.data?.get(Schema.FIELD_DELETED_AT) == null) return
        store.write(WriteOp.Update(path, mapOf(Schema.FIELD_DELETED_AT to null)))
    }

    suspend fun reviewCard(deckId: String, cardId: String, rating: Int, baseState: SrsState? = null) =
        reviews.withLock {
            val paths = paths()
            val path = paths.card(deckId, cardId)
            val card = store.cachedDoc(path)?.let(Schema::readCard)?.takeIf { it.isLive } ?: return@withLock
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
                        mapOf("count" to FieldOp.Increment(1), Schema.FIELD_UPDATED_AT to FieldOp.ServerTime),
                        merge = true
                    )
                )
            }
        }
}
