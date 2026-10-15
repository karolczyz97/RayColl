package com.raycoll.app.data.sync

import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.StoreData

internal fun modeKey(id: String) = "mode/$id"
internal fun activityKey(day: String) = "activity/$day"

internal fun generationFields(data: StoreData): Map<String, Any> = mapOf(
    FirestoreSchema.FIELD_REPLACEMENT_AT to data.replacementAt,
    FirestoreSchema.FIELD_REPLACEMENT_ID to data.replacementId
)

/**
 * Nowa generacja nie widzi starych dokumentów, nawet podczas przerwanej wysyłki wielu porcji.
 * Surowy snapshot zostaje bazą diffu, dzięki czemu następna synchronizacja sprząta resztki.
 * Brak pól w dokumencie oznacza generację zero — zgodność ze starą chmurą.
 */
internal fun visibleCloudGeneration(data: StoreData, generations: Map<String, Pair<Long, String>>): StoreData {
    val expected = data.replacementAt to data.replacementId
    fun matches(key: String) = (generations[key] ?: (0L to "")) == expected
    return data.copy(
        groups = data.groups.filter { matches(deckKey(it.id)) }.map { deck ->
            deck.copy(cards = deck.cards.filter { matches(cardKey(deck.id, it.id)) })
        },
        studyModes = data.studyModes.filter { matches(modeKey(it.id)) },
        activityHeatmap = data.activityHeatmap.filterKeys { matches(activityKey(it)) }
    )
}

internal fun staleGenerationKeys(data: StoreData, generations: Map<String, Pair<Long, String>>): Set<String> =
    generations.filterValues { it != (data.replacementAt to data.replacementId) }.keys

internal fun positionsOfVisible(data: StoreData): Set<String> = buildSet {
    data.groups.forEach { deck ->
        add(deckKey(deck.id))
        deck.cards.forEach { add(cardKey(deck.id, it.id)) }
    }
}
