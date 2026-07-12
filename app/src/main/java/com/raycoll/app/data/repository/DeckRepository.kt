package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import com.raycoll.app.core.LocalDates
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.local.mappers.Mappers
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.operations.addCard
import com.raycoll.app.domain.operations.addGroup
import com.raycoll.app.domain.operations.addGroupWithCards
import com.raycoll.app.domain.operations.archiveGroup
import com.raycoll.app.domain.operations.deleteCard
import com.raycoll.app.domain.operations.deleteGroup
import com.raycoll.app.domain.operations.movePage
import com.raycoll.app.domain.operations.purgeExpiredArchives
import com.raycoll.app.domain.operations.restoreGroup
import com.raycoll.app.domain.operations.reviewCard
import com.raycoll.app.domain.operations.reviewCardAgain
import com.raycoll.app.domain.operations.setActiveStudyMode
import com.raycoll.app.domain.operations.setCardOrder
import com.raycoll.app.domain.operations.setStudyFilter
import com.raycoll.app.domain.operations.setVisiblePageCount
import com.raycoll.app.domain.operations.updateCard
import com.raycoll.app.domain.operations.updateGroup
import com.raycoll.app.domain.seed.SeedData
import com.raycoll.app.domain.normalize.normalizeStoreData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map

@Singleton
class DeckRepository @Inject constructor(
    private val db: RayCollDatabase,
    private val clock: Clock,
    private val namespaceManager: NamespaceManager,
) {
    private val deckDao = db.deckDao()
    private val activityDao = db.activityDao()

    // Sygnal kazdej lokalnej mutacji danych — nasluchuje go SyncLifecycle (debounced push do chmury).
    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val mutations: SharedFlow<Unit> = _mutations
    private fun notifyMutation() { _mutations.tryEmit(Unit) }

    fun activeDecks() = deckDao.activeDecks(namespaceManager.current()).map { entities ->
        entities.map { Mappers.entityToDeck(it, deckDao.getCards(namespaceManager.current(), it.id)) }
    }
    fun archivedDecks() = deckDao.archivedDecks(namespaceManager.current()).map { entities ->
        entities.map { Mappers.entityToDeck(it, deckDao.getCards(namespaceManager.current(), it.id)) }
    }
    fun cardsForDeck(deckId: String) = deckDao.cardsForDeck(namespaceManager.current(), deckId).map { entities ->
        entities.map { Mappers.entityToCard(it) }
    }

    suspend fun getDeck(deckId: String): FlashcardGroup? {
        val ns = namespaceManager.current()
        val deckEntity = deckDao.getDeck(ns, deckId) ?: return null
        val cards = deckDao.getCards(ns, deckId)
        return Mappers.entityToDeck(deckEntity, cards)
    }

    suspend fun addDeck(name: String, languages: List<String>, pageNames: List<String>): String {
        val ns = namespaceManager.current()
        val now = clock.now()
        val (groups, newId) = addGroup(emptyList(), name, languages, pageNames, now)
        val deck = groups.last()
        deckDao.insertDeck(Mappers.deckToEntity(deck, ns))
        notifyMutation()
        return newId
    }

    suspend fun addDeckWithCards(name: String, languages: List<String>, pageNames: List<String>, cards: List<Flashcard>): String {
        val ns = namespaceManager.current()
        val now = clock.now()
        val (groups, newId) = addGroupWithCards(emptyList(), name, languages, pageNames, cards, now)
        val deck = groups.last()
        deckDao.insertDeck(Mappers.deckToEntity(deck, ns))
        deckDao.insertCards(deck.cards.map { Mappers.cardToEntity(it, ns, newId) })
        notifyMutation()
        return newId
    }

    suspend fun updateDeck(deck: FlashcardGroup) {
        val ns = namespaceManager.current()
        val now = clock.now()
        val updated = updateGroup(listOf(deck), deck, now)[0]
        deckDao.insertDeck(Mappers.deckToEntity(updated, ns))
        for (card in updated.cards) deckDao.insertCard(Mappers.cardToEntity(card, ns, updated.id))
        notifyMutation()
    }

    suspend fun deleteDeck(deckId: String) {
        val ns = namespaceManager.current()
        deckDao.softDeleteDeck(ns, deckId, clock.now())
        notifyMutation()
    }

    suspend fun archiveDeck(deckId: String) {
        val ns = namespaceManager.current()
        deckDao.archiveDeck(ns, deckId, clock.now())
        notifyMutation()
    }

    suspend fun restoreDeck(deckId: String) {
        val ns = namespaceManager.current()
        deckDao.restoreDeck(ns, deckId, clock.now())
        notifyMutation()
    }

    suspend fun addCard(deckId: String, pages: List<String>): String {
        val ns = namespaceManager.current()
        val now = clock.now()
        val deck = getDeck(deckId) ?: throw IllegalStateException("Deck not found")
        val (_, cardId) = addCard(listOf(deck), deckId, pages, now)
        val card = Flashcard(id = cardId, pages = pages, srsState = SrsState(), contentUpdatedAt = now)
        deckDao.insertCard(Mappers.cardToEntity(card, ns, deckId))
        notifyMutation()
        return cardId
    }

    suspend fun updateCard(deckId: String, card: Flashcard) {
        val ns = namespaceManager.current()
        val now = clock.now()
        deckDao.insertCard(Mappers.cardToEntity(card.copy(contentUpdatedAt = now), ns, deckId))
        notifyMutation()
    }

    suspend fun deleteCard(deckId: String, cardId: String) {
        val ns = namespaceManager.current()
        deckDao.softDeleteCard(ns, cardId, clock.now())
        notifyMutation()
    }

    suspend fun reviewCard(deckId: String, cardId: String, rating: Int) {
        val ns = namespaceManager.current()
        val now = clock.now()
        val deck = getDeck(deckId) ?: return
        val updated = reviewCard(listOf(deck), deckId, cardId, rating, now)
        val card = updated[0].cards.find { it.id == cardId } ?: return
        deckDao.insertCard(Mappers.cardToEntity(card, ns, deckId))
        val dayKey = LocalDates.dayKey(now)
        activityDao.incrementActivity(ns, dayKey)
        notifyMutation()
    }

    suspend fun reviewCardAgain(deckId: String, cardId: String, rating: Int, baseState: SrsState) {
        val ns = namespaceManager.current()
        val now = clock.now()
        val deck = getDeck(deckId) ?: return
        val updated = reviewCardAgain(listOf(deck), deckId, cardId, rating, baseState, now)
        val card = updated[0].cards.find { it.id == cardId } ?: return
        deckDao.insertCard(Mappers.cardToEntity(card, ns, deckId))
        notifyMutation()
    }

    suspend fun setVisiblePageCount(deckId: String, count: Int) {
        val ns = namespaceManager.current()
        val deck = getDeck(deckId) ?: return
        val updated = setVisiblePageCount(listOf(deck), deckId, count, clock.now())
        deckDao.insertDeck(Mappers.deckToEntity(updated[0], ns))
        notifyMutation()
    }

    suspend fun setStudyFilter(deckId: String, filter: String) {
        val ns = namespaceManager.current()
        val deck = getDeck(deckId) ?: return
        val updated = setStudyFilter(listOf(deck), deckId, filter, clock.now())
        deckDao.insertDeck(Mappers.deckToEntity(updated[0], ns))
        notifyMutation()
    }

    suspend fun setCardOrder(deckId: String, order: String) {
        val ns = namespaceManager.current()
        val deck = getDeck(deckId) ?: return
        val updated = setCardOrder(listOf(deck), deckId, order, clock.now())
        deckDao.insertDeck(Mappers.deckToEntity(updated[0], ns))
        notifyMutation()
    }

    suspend fun setActiveStudyMode(deckId: String, modeId: String) {
        val ns = namespaceManager.current()
        val deck = getDeck(deckId) ?: return
        val updated = setActiveStudyMode(listOf(deck), deckId, modeId, clock.now())
        deckDao.insertDeck(Mappers.deckToEntity(updated[0], ns))
        notifyMutation()
    }

    suspend fun movePage(deckId: String, from: Int, to: Int) {
        val ns = namespaceManager.current()
        val deck = getDeck(deckId) ?: return
        val updated = movePage(listOf(deck), deckId, from, to, clock.now())
        deckDao.insertDeck(Mappers.deckToEntity(updated[0], ns))
        for (card in updated[0].cards) deckDao.insertCard(Mappers.cardToEntity(card, ns, deckId))
        notifyMutation()
    }

    suspend fun purgeExpiredArchives(): Boolean {
        val ns = namespaceManager.current()
        val now = clock.now()
        val retentionMs = com.raycoll.app.domain.model.ArchiveConstants.RETENTION_DAYS * com.raycoll.app.domain.model.SrsConstants.DAY_MS
        val decks = deckDao.allDecks(ns)
        val groups = decks.map { Mappers.entityToDeck(it, deckDao.getCards(ns, it.id)) }
        val (updated, changed) = purgeExpiredArchives(groups, now, retentionMs)
        if (changed) {
            for (deck in updated) {
                if (deck.deletedAt != null) deckDao.softDeleteDeck(ns, deck.id, deck.deletedAt)
            }
            notifyMutation()
        }
        return changed
    }

    suspend fun seedIfEmpty() {
        val ns = namespaceManager.current()
        val existing = deckDao.allDecks(ns)
        if (existing.isNotEmpty()) return
        val seedData = SeedData.createSeedData()
        val normalized = normalizeStoreData(seedData)
        for (deck in normalized.groups) {
            deckDao.insertDeck(Mappers.deckToEntity(deck, ns))
            deckDao.insertCards(deck.cards.map { Mappers.cardToEntity(it, ns, deck.id) })
        }
    }

    suspend fun getSnapshot(): StoreData {
        val ns = namespaceManager.current()
        val decks = deckDao.allDecks(ns)
        val groups = decks.map { Mappers.entityToDeck(it, deckDao.getCards(ns, it.id)) }
        val modes = db.studyModeDao().allModes(ns).map { Mappers.entityToMode(it) }
        val heatmap = db.activityDao().allActivity(ns).associate { Mappers.entityToActivity(it) }
        return StoreData(groups = groups, studyModes = modes, activityHeatmap = heatmap)
    }

    suspend fun replaceAll(snapshot: StoreData) {
        val ns = namespaceManager.current()
        val normalized = normalizeStoreData(snapshot)
        deckDao.deleteAllDecks(ns)
        db.studyModeDao().deleteAllModes(ns)
        activityDao.deleteAllActivity(ns)
        for (deck in normalized.groups) {
            deckDao.insertDeck(Mappers.deckToEntity(deck, ns))
            deckDao.insertCards(deck.cards.map { Mappers.cardToEntity(it, ns, deck.id) })
        }
        db.studyModeDao().insertModes(normalized.studyModes.map { Mappers.modeToEntity(it, ns) })
        for ((dayKey, count) in normalized.activityHeatmap) {
            activityDao.upsertActivity(Mappers.activityToEntity(dayKey, count, ns))
        }
        notifyMutation()
    }
}
