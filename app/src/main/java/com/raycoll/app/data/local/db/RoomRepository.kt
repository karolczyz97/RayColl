package com.raycoll.app.data.local.db

import com.raycoll.app.domain.model.*
import com.raycoll.app.domain.normalize.normalizeStoreData
import com.raycoll.app.domain.seed.createSeedGroups
import com.raycoll.app.domain.seed.createSeedModes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val appJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
inline fun <reified T> myEncode(value: T): String = appJson.encodeToString(value)
inline fun <reified T> myDecode(json: String): T = appJson.decodeFromString(json)

@Singleton
class RoomRepository @Inject constructor(private val db: RayCollDatabase) {
    private val gDao = db.groupDao(); private val cDao = db.cardDao()
    private val mDao = db.studyModeDao(); private val dDao = db.activityDayDao()

    fun observeStoreData(): Flow<StoreData> = combine(
        gDao.activeGroups().combine(gDao.archivedGroups()) { a, b -> a + b },
        mDao.liveModes(), dDao.heatmap(),
    ) { groups, modes, days ->
        StoreData(groups.map { it.toDomain(cDao) }, modes.map { it.toDomain() }, days.associate { it.date to it.count })
    }

    suspend fun loadStoreData(): StoreData {
        val ents = gDao.allGroups()
        val g = ents.map { it.toDomain(cDao) }
        val m = mDao.allModes().map { it.toDomain() }
        val h = dDao.allDays().associate { it.date to it.count }
        return normalizeStoreData(StoreData(g, m, h))
    }

    suspend fun saveStoreData(data: StoreData) {
        val nd = normalizeStoreData(data)
        gDao.replaceAll(nd.groups.map { it.toEntity() })
        for (gr in nd.groups) cDao.replaceAllForGroup(gr.id, gr.cards.map { it.toEntity(gr.id) })
        mDao.replaceAll(nd.studyModes.map { it.toEntity() })
        dDao.replaceAll(nd.activityHeatmap.map { (d, c) -> ActivityDayEntity(d, c) })
    }

    suspend fun seedIfEmpty() { if (gDao.allGroups().isEmpty()) { val d = normalizeStoreData(StoreData(createSeedGroups(), createSeedModes())); saveStoreData(d) } }

    private fun GroupEntity.toDomain(cDao: CardDao): FlashcardGroup {
        val cards = kotlinx.coroutines.runBlocking { cDao.allCardsByGroup(id).map { it.toDomain() } }
        val pn: List<String> = try { myDecode(pageNames) } catch (_: Exception) { emptyList() }
        val pl: List<String> = try { myDecode(pageLanguages) } catch (_: Exception) { emptyList() }
        return FlashcardGroup(id, name, cards, activeModeId, CardFilter.valueOf(studyFilter), CardOrder.valueOf(cardOrder), pl, pn, activePageCount, updatedAt, deletedAt, archivedAt)
    }

    private fun CardEntity.toDomain(): Flashcard {
        val pages: List<String> = try { appJson.decodeFromString(pages) } catch (_: Exception) { emptyList() }
        return Flashcard(id, pages, SrsState(srsDifficulty, srsStability, srsRepetitions, srsState, srsLastReview, srsNextReview), contentUpdatedAt, srsUpdatedAt, deletedAt)
    }

    private fun StudyModeEntity.toDomain(): StudyMode {
        val steps: List<ModeStep> = try { appJson.decodeFromString(steps) } catch (_: Exception) { emptyList() }
        return StudyMode(id, name, steps, isBuiltIn, builtInSourceId, updatedAt, deletedAt)
    }

    private fun FlashcardGroup.toEntity() = GroupEntity(id, name, activeModeId, myEncode(pageNames), myEncode(pageLanguages), activePageCount, studyFilter.name, cardOrder.name, updatedAt, deletedAt, archivedAt)
    private fun Flashcard.toEntity(groupId: String) = CardEntity(id, groupId, myEncode(pages), srsState.difficulty, srsState.stability, srsState.repetitions, srsState.state, srsState.lastReviewTimestamp, srsState.nextReviewTimestamp, contentUpdatedAt, srsUpdatedAt, deletedAt)
    private fun StudyMode.toEntity() = StudyModeEntity(id, name, myEncode(steps), isBuiltIn, builtInSourceId, updatedAt, deletedAt)
}
