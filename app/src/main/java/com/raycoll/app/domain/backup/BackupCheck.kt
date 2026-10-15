package com.raycoll.app.domain.backup

import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.SrsStates
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.referencedPage
import com.raycoll.app.domain.normalize.normalizeModeStep
import com.raycoll.app.domain.steps.CompoundExpander
import java.time.LocalDate

/** Sprawdza typowaną kopię przed pierwszym zapisem. Niczego po cichu nie poprawia. */
object BackupCheck {
    fun validate(data: StoreData) {
        fun id(value: String) {
            require(value.isNotBlank() && '/' !in value && value != "." && value != "..")
        }
        fun ids(values: List<String>) {
            values.forEach(::id)
            require(values.distinct().size == values.size)
        }
        ids(data.groups.map { it.id })
        ids(data.groups.flatMap { it.cards }.map { it.id })
        ids(data.studyModes.map { it.id })
        data.groups.forEach { deck ->
            require(deck.name.isNotBlank())
            id(deck.activeModeId)
            require(deck.activePageCount in PageLimits.MIN_PAGE_COUNT..PageLimits.MAX_VISIBLE_PAGE_COUNT)
            require(deck.pageNames.size in deck.activePageCount..PageLimits.MAX_STORED_PAGE_COUNT)
            require(deck.pageLanguages.size in deck.activePageCount..PageLimits.MAX_STORED_PAGE_COUNT)
            require(deck.updatedAt >= 0 && (deck.deletedAt ?: 0) >= 0 && (deck.archivedAt ?: 0) >= 0)
            deck.cards.forEach { card ->
                require(card.pages.size in PageLimits.MIN_PAGE_COUNT..PageLimits.MAX_STORED_PAGE_COUNT)
                require(card.contentUpdatedAt >= 0 && card.srsUpdatedAt >= 0 && (card.deletedAt ?: 0) >= 0)
                val srs = card.srsState
                require(srs.difficulty.isFinite() && srs.difficulty in 1.0..10.0)
                require(srs.stability.isFinite() && srs.stability >= 0 && srs.repetitions >= 0)
                require(srs.state in SrsStates.VALID && srs.lastReviewTimestamp >= 0 && srs.nextReviewTimestamp >= 0)
            }
        }
        data.studyModes.forEach { mode ->
            require(mode.name.isNotBlank() && mode.updatedAt >= 0 && (mode.deletedAt ?: 0) >= 0)
            if (mode.isBuiltIn) id(requireNotNull(mode.builtInSourceId)) else require(mode.builtInSourceId == null)
            mode.steps.forEach { require(normalizeModeStep(it) == it) }
            CompoundExpander.expandModeSteps(mode.steps).forEach { step ->
                step.referencedPage?.let { require(it in 0 until PageLimits.MAX_STORED_PAGE_COUNT) }
            }
        }
        data.activityHeatmap.forEach { (day, count) ->
            require(runCatching { LocalDate.parse(day).toString() }.getOrNull() == day && count >= 0)
        }
    }
}
