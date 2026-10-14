package com.raycoll.app.domain.merge

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.normalize.normalizeStoreData
import com.raycoll.app.domain.seed.isBuiltInModeSourceId

private fun latestDelete(a: Long?, b: Long?): Long = maxOf(a ?: 0, b ?: 0)

private fun isAlive(latestEdit: Long, deletedAt: Long): Boolean = deletedAt <= 0 || latestEdit > deletedAt

private fun getLatestEdit(card: Flashcard): Long = maxOf(card.contentUpdatedAt, card.srsUpdatedAt)

private fun localSrsWins(localReps: Int, localSrsAt: Long, cloudReps: Int, cloudSrsAt: Long): Boolean = when {
    localReps > cloudReps -> true
    cloudReps > localReps -> false
    localSrsAt > cloudSrsAt -> true
    else -> false
}

fun mergeActivityHeatmap(local: Map<String, Int>, cloud: Map<String, Int>): Map<String, Int> {
    val merged = cloud.toMutableMap()
    for ((date, count) in local) merged[date] = maxOf(merged[date] ?: 0, count)
    return merged
}

fun mergeCards(localCards: List<Flashcard>, cloudCards: List<Flashcard>): List<Flashcard> {
    val allIds = (localCards.map { it.id } + cloudCards.map { it.id }).toSet()
    val localMap = localCards.associateBy { it.id }
    val cloudMap = cloudCards.associateBy { it.id }
    val merged = mutableListOf<Flashcard>()

    for (id in allIds) {
        val local = localMap[id]
        val cloud = cloudMap[id]

        if (local == null && cloud != null) {
            val latestEdit = getLatestEdit(cloud)
            val delAt = cloud.deletedAt ?: 0
            merged.add(if (delAt > 0 && latestEdit > delAt) cloud.copy(deletedAt = null) else cloud)
            continue
        }
        if (cloud == null && local != null) {
            val latestEdit = getLatestEdit(local)
            val delAt = local.deletedAt ?: 0
            merged.add(if (delAt > 0 && latestEdit > delAt) local.copy(deletedAt = null) else local)
            continue
        }
        val l = local!!
        val c = cloud!!

        val deletedAt = latestDelete(l.deletedAt, c.deletedAt)
        val latestEdit = maxOf(getLatestEdit(l), getLatestEdit(c))

        if (!isAlive(latestEdit, deletedAt)) {
            merged.add(c.copy(deletedAt = if (deletedAt > 0) deletedAt else null))
            continue
        }

        val preferLocalSrs =
            localSrsWins(l.srsState.repetitions, l.srsUpdatedAt, c.srsState.repetitions, c.srsUpdatedAt)
        val contentSide = when {
            l.contentUpdatedAt > c.contentUpdatedAt -> l
            c.contentUpdatedAt > l.contentUpdatedAt -> c
            preferLocalSrs -> l
            else -> c
        }

        merged.add(
            Flashcard(
                id = id,
                pages = contentSide.pages,
                srsState = if (preferLocalSrs) l.srsState else c.srsState,
                contentUpdatedAt = contentSide.contentUpdatedAt,
                srsUpdatedAt = if (preferLocalSrs) l.srsUpdatedAt else c.srsUpdatedAt,
                deletedAt = null // edycja nowsza niż kasowanie = ożywienie (isAlive)
            )
        )
    }
    return merged
}

fun mergeGroups(localGroups: List<FlashcardGroup>, cloudGroups: List<FlashcardGroup>): List<FlashcardGroup> {
    val allIds = (localGroups.map { it.id } + cloudGroups.map { it.id }).toSet()
    val localMap = localGroups.associateBy { it.id }
    val cloudMap = cloudGroups.associateBy { it.id }
    val merged = mutableListOf<FlashcardGroup>()

    for (id in allIds) {
        val local = localMap[id]
        val cloud = cloudMap[id]

        if (local == null &&
            cloud != null
        ) {
            merged.add(cloud.copy(cards = mergeCards(emptyList(), cloud.cards)))
            continue
        }
        if (cloud == null &&
            local != null
        ) {
            merged.add(local.copy(cards = mergeCards(local.cards, emptyList())))
            continue
        }

        val l = local!!
        val c = cloud!!
        val deletedAt = latestDelete(l.deletedAt, c.deletedAt)
        val latestEdit = maxOf(l.updatedAt, c.updatedAt)

        if (!isAlive(latestEdit, deletedAt)) {
            merged.add(c.copy(cards = mergeCards(l.cards, c.cards), deletedAt = if (deletedAt > 0) deletedAt else null))
            continue
        }

        val winner = if (l.updatedAt > c.updatedAt) l else c
        merged.add(
            winner.copy(
                cards = mergeCards(l.cards, c.cards),
                updatedAt = latestEdit,
                deletedAt = null // edycja nowsza niż kasowanie = ożywienie (isAlive)
            )
        )
    }
    return merged
}

fun mergeStudyModes(localModes: List<StudyMode>, cloudModes: List<StudyMode>): List<StudyMode> {
    val allIds = (localModes.map { it.id } + cloudModes.map { it.id }).toSet()
    val localMap = localModes.associateBy { it.id }
    val cloudMap = cloudModes.associateBy { it.id }
    val merged = mutableListOf<StudyMode>()

    for (id in allIds) {
        val local = localMap[id]
        val cloud = cloudMap[id]

        if (local == null && cloud != null) {
            merged.add(cloud)
            continue
        }
        if (cloud == null && local != null) {
            merged.add(local)
            continue
        }

        val l = local!!
        val c = cloud!!
        val deletedAt = latestDelete(l.deletedAt, c.deletedAt)
        val latestEdit = maxOf(l.updatedAt, c.updatedAt)

        if (!isAlive(latestEdit, deletedAt)) {
            val sourceId = l.builtInSourceId ?: c.builtInSourceId
            if (sourceId != null && isBuiltInModeSourceId(sourceId)) {
                val winner = if (l.updatedAt > c.updatedAt) l else c
                merged.add(winner.copy(updatedAt = latestEdit, deletedAt = null))
                continue
            }
            merged.add(c.copy(deletedAt = if (deletedAt > 0) deletedAt else null))
            continue
        }

        val winner = if (l.updatedAt > c.updatedAt) l else c
        merged.add(winner.copy(updatedAt = latestEdit, deletedAt = null))
    }
    return merged
}

fun mergeUserData(local: StoreData, cloud: StoreData): StoreData {
    val mergedGroups = mergeGroups(local.groups, cloud.groups)
    val mergedModes = mergeStudyModes(local.studyModes, cloud.studyModes)
    val mergedHeatmap = mergeActivityHeatmap(local.activityHeatmap, cloud.activityHeatmap)
    return normalizeStoreData(
        StoreData(groups = mergedGroups, studyModes = mergedModes, activityHeatmap = mergedHeatmap)
    )
}
