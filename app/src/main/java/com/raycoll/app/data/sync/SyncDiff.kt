package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode

/** Pojedyncza operacja zapisu/kasowania dokumentu w chmurze. */
sealed interface SyncOp {
    data class PutDeck(val deck: FlashcardGroup) : SyncOp
    data class DeleteDeck(val deckId: String) : SyncOp
    data class PutCard(val deckId: String, val card: Flashcard) : SyncOp
    data class DeleteCard(val deckId: String, val cardId: String) : SyncOp
    data class PutMode(val mode: StudyMode) : SyncOp
    data class DeleteMode(val modeId: String) : SyncOp
    data class PutActivity(val dayKey: String, val count: Int) : SyncOp
    data class DeleteActivity(val dayKey: String) : SyncOp
}

/** Wyliczanie operacji zapisu do chmury: `previous` = znany stan chmury, `data` = stan lokalny. */
object SyncDiff {

    /**
     * Operacje zapisu względem ostatniego znanego stanu chmury: tylko dokumenty
     * nowe lub zmienione oraz usunięcie tych, których już nie ma. Talia porównywana
     * bez kart — karty to osobne dokumenty.
     */
    fun diffSave(previous: StoreData, data: StoreData): List<SyncOp> {
        val ops = mutableListOf<SyncOp>()

        val previousGroups = previous.groups.associateBy { it.id }.toMutableMap()
        for (group in data.groups) {
            val prevGroup = previousGroups.remove(group.id)
            if (prevGroup == null || prevGroup.copy(cards = emptyList()) != group.copy(cards = emptyList())) {
                ops.add(SyncOp.PutDeck(group))
            }
            val prevCards = prevGroup?.cards?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
            for (card in group.cards) {
                val prevCard = prevCards.remove(card.id)
                if (prevCard == null || prevCard != card) ops.add(SyncOp.PutCard(group.id, card))
            }
            for (cardId in prevCards.keys) ops.add(SyncOp.DeleteCard(group.id, cardId))
        }
        for (removed in previousGroups.values) {
            for (card in removed.cards) ops.add(SyncOp.DeleteCard(removed.id, card.id))
            ops.add(SyncOp.DeleteDeck(removed.id))
        }

        val previousModes = previous.studyModes.associateBy { it.id }.toMutableMap()
        for (mode in data.studyModes) {
            val prevMode = previousModes.remove(mode.id)
            if (prevMode == null || prevMode != mode) ops.add(SyncOp.PutMode(mode))
        }
        for (modeId in previousModes.keys) ops.add(SyncOp.DeleteMode(modeId))

        val previousDays = previous.activityHeatmap.toMutableMap()
        for ((dayKey, count) in data.activityHeatmap) {
            val prevCount = previousDays.remove(dayKey)
            if (prevCount != count) ops.add(SyncOp.PutActivity(dayKey, count))
        }
        for (dayKey in previousDays.keys) ops.add(SyncOp.DeleteActivity(dayKey))

        return ops
    }
}
