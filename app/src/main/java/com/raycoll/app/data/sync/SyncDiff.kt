package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode

/** Pojedyncza operacja zapisu/kasowania dokumentu w chmurze. */
sealed interface SyncOp {
    data class PutDeck(val deck: FlashcardGroup, val position: Int) : SyncOp
    data class DeleteDeck(val deckId: String) : SyncOp
    data class PutCard(val deckId: String, val card: Flashcard, val position: Int) : SyncOp
    data class DeleteCard(val deckId: String, val cardId: String) : SyncOp
    data class PutMode(val mode: StudyMode) : SyncOp
    data class DeleteMode(val modeId: String) : SyncOp
    data class PutActivity(val dayKey: String, val count: Int) : SyncOp
    data class DeleteActivity(val dayKey: String) : SyncOp
}

/** Klucz talii w zbiorach pozycji; id Firestore nie zawierają "/". */
internal fun deckKey(deckId: String) = deckId

internal fun cardKey(deckId: String, cardId: String) = "$deckId/$cardId"

/** Pusty diff nie przesuwa bazy; sama nowa generacja wymaga publikacji znacznika. */
internal fun cloudCommitRequired(previous: StoreData, data: StoreData, ops: List<SyncOp>): Boolean =
    ops.isNotEmpty() || previous.replacementAt != data.replacementAt || previous.replacementId != data.replacementId

/** Wyliczanie operacji zapisu do chmury: `previous` = znany stan chmury, `data` = stan lokalny. */
object SyncDiff {

    /**
     * Operacje zapisu względem ostatniego znanego stanu chmury: tylko dokumenty
     * nowe lub zmienione oraz usunięcie tych, których już nie ma. Talia porównywana
     * bez kart — karty to osobne dokumenty. Dokument jest też zapisywany ponownie, gdy zmieniło się
     * jego miejsce na liście albo klucz jest w [stalePositions] (chmura trzyma w nim inną pozycję
     * niż miejsce na liście, np. dokument sprzed pola `position`).
     */
    fun diffSave(previous: StoreData, data: StoreData, stalePositions: Set<String> = emptySet()): List<SyncOp> {
        val ops = mutableListOf<SyncOp>()
        val replacing = previous.replacementAt != data.replacementAt || previous.replacementId != data.replacementId

        val previousGroups = previous.groups.withIndex().associateBy { it.value.id }.toMutableMap()
        for ((index, group) in data.groups.withIndex()) {
            val prevGroup = previousGroups.remove(group.id)
            if (replacing || prevGroup == null || prevGroup.index != index || deckKey(group.id) in stalePositions ||
                prevGroup.value.copy(cards = emptyList()) != group.copy(cards = emptyList())
            ) {
                ops.add(SyncOp.PutDeck(group, index))
            }
            val prevCards = prevGroup?.value?.cards?.withIndex()?.associateBy { it.value.id }?.toMutableMap()
                ?: mutableMapOf()
            for ((cardIndex, card) in group.cards.withIndex()) {
                val prevCard = prevCards.remove(card.id)
                if (replacing || prevCard == null || prevCard.index != cardIndex ||
                    cardKey(group.id, card.id) in stalePositions ||
                    prevCard.value != card
                ) {
                    ops.add(SyncOp.PutCard(group.id, card, cardIndex))
                }
            }
            for (cardId in prevCards.keys) ops.add(SyncOp.DeleteCard(group.id, cardId))
        }
        for (removed in previousGroups.values) {
            for (card in removed.value.cards) ops.add(SyncOp.DeleteCard(removed.value.id, card.id))
            ops.add(SyncOp.DeleteDeck(removed.value.id))
        }

        val previousModes = previous.studyModes.associateBy { it.id }.toMutableMap()
        for (mode in data.studyModes) {
            val prevMode = previousModes.remove(mode.id)
            if (replacing || modeKey(mode.id) in stalePositions || prevMode != mode) ops.add(SyncOp.PutMode(mode))
        }
        for (modeId in previousModes.keys) ops.add(SyncOp.DeleteMode(modeId))

        val previousDays = previous.activityHeatmap.toMutableMap()
        for ((dayKey, count) in data.activityHeatmap) {
            val prevCount = previousDays.remove(dayKey)
            if (replacing || activityKey(dayKey) in stalePositions ||
                prevCount != count
            ) {
                ops.add(SyncOp.PutActivity(dayKey, count))
            }
        }
        for (dayKey in previousDays.keys) ops.add(SyncOp.DeleteActivity(dayKey))

        return ops
    }
}
