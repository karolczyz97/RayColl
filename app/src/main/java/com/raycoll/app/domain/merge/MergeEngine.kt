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

internal fun mergeActivityHeatmap(local: Map<String, Int>, cloud: Map<String, Int>): Map<String, Int> {
    val merged = cloud.toMutableMap()
    for ((date, count) in local) merged[date] = maxOf(merged[date] ?: 0, count)
    return merged
}

/**
 * Suma list po id: [both] scala element obecny po obu stronach, [single] obsługuje element tylko z jednej.
 * Kolejność: najpierw id lokalne, potem te tylko z chmury (kolejność kart w talii zależy od zapisu);
 * z [preferCloudOrder] odwrotnie — najpierw kolejność chmury, żeby dwa urządzenia doszły do tej samej.
 */
private inline fun <T> mergeById(
    local: List<T>,
    cloud: List<T>,
    id: (T) -> String,
    both: (T, T) -> T,
    single: (T) -> T,
    preferCloudOrder: Boolean = false
): List<T> {
    val localMap = local.associateBy(id)
    val cloudMap = cloud.associateBy(id)
    val keys = if (preferCloudOrder) cloud.map(id) + local.map(id) else local.map(id) + cloud.map(id)
    return keys.toSet().map { key ->
        val l = localMap[key]
        val c = cloudMap[key]
        if (l != null && c != null) both(l, c) else single(requireNotNull(l ?: c))
    }
}

fun mergeCards(
    localCards: List<Flashcard>,
    cloudCards: List<Flashcard>,
    preferCloudOrder: Boolean = false
): List<Flashcard> = mergeById(
    local = localCards,
    cloud = cloudCards,
    id = { it.id },
    preferCloudOrder = preferCloudOrder,
    single = { card ->
        val delAt = card.deletedAt ?: 0
        if (delAt > 0 && getLatestEdit(card) > delAt) card.copy(deletedAt = null) else card
    },
    both = { l, c ->
        val deletedAt = latestDelete(l.deletedAt, c.deletedAt)
        val latestEdit = maxOf(getLatestEdit(l), getLatestEdit(c))
        if (!isAlive(latestEdit, deletedAt)) {
            c.copy(deletedAt = deletedAt.takeIf { it > 0 })
        } else {
            val preferLocalSrs =
                localSrsWins(l.srsState.repetitions, l.srsUpdatedAt, c.srsState.repetitions, c.srsUpdatedAt)
            val contentSide = when {
                l.contentUpdatedAt > c.contentUpdatedAt -> l
                c.contentUpdatedAt > l.contentUpdatedAt -> c
                preferLocalSrs -> l
                else -> c
            }
            Flashcard(
                id = l.id,
                pages = contentSide.pages,
                srsState = if (preferLocalSrs) l.srsState else c.srsState,
                contentUpdatedAt = contentSide.contentUpdatedAt,
                srsUpdatedAt = if (preferLocalSrs) l.srsUpdatedAt else c.srsUpdatedAt,
                deletedAt = null // edycja nowsza niż kasowanie = ożywienie (isAlive)
            )
        }
    }
)

fun mergeGroups(
    localGroups: List<FlashcardGroup>,
    cloudGroups: List<FlashcardGroup>,
    preferCloudOrder: Boolean = false
): List<FlashcardGroup> = mergeById(
    local = localGroups,
    cloud = cloudGroups,
    id = { it.id },
    preferCloudOrder = preferCloudOrder,
    single = { group -> group.copy(cards = mergeCards(group.cards, emptyList())) },
    both = { l, c ->
        val deletedAt = latestDelete(l.deletedAt, c.deletedAt)
        val latestEdit = maxOf(l.updatedAt, c.updatedAt)
        if (!isAlive(latestEdit, deletedAt)) {
            c.copy(cards = mergeCards(l.cards, c.cards, preferCloudOrder), deletedAt = deletedAt.takeIf { it > 0 })
        } else {
            val winner = if (l.updatedAt > c.updatedAt) l else c
            winner.copy(
                cards = mergeCards(l.cards, c.cards, preferCloudOrder),
                updatedAt = latestEdit,
                deletedAt = null // edycja nowsza niż kasowanie = ożywienie (isAlive)
            )
        }
    }
)

fun mergeStudyModes(localModes: List<StudyMode>, cloudModes: List<StudyMode>): List<StudyMode> = mergeById(
    local = localModes,
    cloud = cloudModes,
    id = { it.id },
    single = { it },
    both = { l, c ->
        val deletedAt = latestDelete(l.deletedAt, c.deletedAt)
        val latestEdit = maxOf(l.updatedAt, c.updatedAt)
        val winner = if (l.updatedAt > c.updatedAt) l else c
        if (isAlive(latestEdit, deletedAt)) {
            winner.copy(updatedAt = latestEdit, deletedAt = null)
        } else {
            val sourceId = l.builtInSourceId ?: c.builtInSourceId
            if (sourceId != null && isBuiltInModeSourceId(sourceId)) {
                // wbudowanego trybu nie da się usunąć na stałe: kasowanie przegrywa
                winner.copy(updatedAt = latestEdit, deletedAt = null)
            } else {
                c.copy(deletedAt = deletedAt.takeIf { it > 0 })
            }
        }
    }
)

fun mergeUserData(local: StoreData, cloud: StoreData, preferCloudOrder: Boolean = false): StoreData {
    val generationOrder = compareValuesBy(local, cloud, StoreData::replacementAt, StoreData::replacementId)
    if (generationOrder != 0) return normalizeStoreData(if (generationOrder > 0) local else cloud)
    val mergedGroups = mergeGroups(local.groups, cloud.groups, preferCloudOrder)
    val mergedModes = mergeStudyModes(local.studyModes, cloud.studyModes)
    val mergedHeatmap = mergeActivityHeatmap(local.activityHeatmap, cloud.activityHeatmap)
    return normalizeStoreData(
        local.copy(groups = mergedGroups, studyModes = mergedModes, activityHeatmap = mergedHeatmap)
    )
}
