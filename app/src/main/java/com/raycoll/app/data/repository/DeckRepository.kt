package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.db.RoomRepository
import com.raycoll.app.data.local.db.myEncode
import com.raycoll.app.data.local.db.myDecode
import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.normalize.normalizeStoreData
import com.raycoll.app.domain.operations.*
import com.raycoll.app.domain.session.ReviewEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepository @Inject constructor(private val room: RoomRepository, private val clock: Clock) {
    val storeData: Flow<StoreData> get() = room.observeStoreData()

    suspend fun init() { room.seedIfEmpty() }
    suspend fun load(): StoreData = room.loadStoreData()
    suspend fun save(data: StoreData) { room.saveStoreData(normalizeStoreData(data)) }

    suspend fun addFlashcard(groupId: String, pages: List<String>): String {
        val d = load(); val (g, id) = addFlashcard(d.groups, groupId, pages, clock.millis()); save(d.copy(groups = g)); return id
    }
    suspend fun reviewCard(groupId: String, cardId: String, rating: Int, todayKey: String) {
        val d = load(); val (g, h, _) = reviewFlashcard(d.groups, groupId, cardId, rating, d.activityHeatmap, todayKey, clock.millis()); save(d.copy(groups = g, activityHeatmap = h))
    }
    suspend fun reviewCardAgain(groupId: String, cardId: String, rating: Int, baseSrsState: SrsState) {
        val d = load(); save(d.copy(groups = reviewFlashcardAgain(d.groups, groupId, cardId, rating, baseSrsState, clock.millis())))
    }
    suspend fun addGroup(name: String, languages: List<String>, pageNames: List<String>): String {
        val d = load(); val (g, id) = addGroup(d.groups, name, languages, pageNames, clock.millis()); save(d.copy(groups = g)); return id
    }
    suspend fun updateGroup(group: FlashcardGroup) { val d = load(); save(d.copy(groups = updateGroup(d.groups, group, clock.millis()))) }
    suspend fun archiveGroup(groupId: String) { val d = load(); save(d.copy(groups = archiveGroup(d.groups, groupId, clock.millis()))) }
    suspend fun restoreGroup(groupId: String) { val d = load(); save(d.copy(groups = restoreGroup(d.groups, groupId, clock.millis()))) }
    suspend fun deleteGroup(groupId: String) { val d = load(); save(d.copy(groups = deleteGroup(d.groups, groupId, clock.millis()))) }
    suspend fun purgeArchives(): Boolean { val d = load(); val (g, c) = purgeExpiredArchives(d.groups, clock.millis()); if (c) save(d.copy(groups = g)); return c }

    suspend fun reviewBatch(reviews: List<ReviewEntry>, todayKey: String) {
        val d = load(); var groups = d.groups; var heatmap = d.activityHeatmap
        for (r in reviews) {
            if (r.replaceFromBase) groups = reviewFlashcardAgain(groups, r.groupId, r.cardId, r.rating, r.baseSrsState, clock.millis())
            else { val (g, h, _) = reviewFlashcard(groups, r.groupId, r.cardId, r.rating, heatmap, todayKey, clock.millis()); groups = g; heatmap = h }
        }
        save(d.copy(groups = groups, activityHeatmap = heatmap))
    }

    suspend fun updateGroupCards(groupId: String, cards: List<Flashcard>) {
        val d = load(); save(d.copy(groups = d.groups.map { if (it.id == groupId) it.copy(cards = cards, updatedAt = clock.millis()) else it }))
    }

    suspend fun exportJson(): String = myEncode(load())
    suspend fun importJson(json: String) { save(myDecode<StoreData>(json)) }
}
