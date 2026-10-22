package com.raycoll.app.domain.backup

import com.raycoll.app.domain.model.PageLimits
import com.raycoll.app.domain.model.SrsConstants
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
            require(value.isNotBlank() && '/' !in value && value != "." && value != "..") { "invalid id '$value'" }
        }
        fun ids(values: List<String>) {
            values.forEach(::id)
            require(values.distinct().size == values.size) { "duplicate ids" }
        }
        ids(data.groups.map { it.id })
        ids(data.groups.flatMap { it.cards }.map { it.id })
        ids(data.studyModes.map { it.id })
        data.groups.forEach { deck ->
            val where = "deck ${deck.id}"
            require(deck.name.isNotBlank()) { "$where: blank name" }
            id(deck.activeModeId)
            require(deck.activePageCount in PageLimits.MIN_PAGE_COUNT..PageLimits.MAX_VISIBLE_PAGE_COUNT) {
                "$where: activePageCount ${deck.activePageCount}"
            }
            require(deck.pageNames.size in deck.activePageCount..PageLimits.MAX_STORED_PAGE_COUNT) {
                "$where: ${deck.pageNames.size} page names"
            }
            require(deck.pageLanguages.size in deck.activePageCount..PageLimits.MAX_STORED_PAGE_COUNT) {
                "$where: ${deck.pageLanguages.size} page languages"
            }
            require(deck.updatedAt >= 0 && (deck.deletedAt ?: 0) >= 0 && (deck.archivedAt ?: 0) >= 0) {
                "$where: negative timestamp"
            }
            deck.cards.forEach { card ->
                val cardWhere = "card ${card.id}"
                require(card.pages.size in PageLimits.MIN_PAGE_COUNT..PageLimits.MAX_STORED_PAGE_COUNT) {
                    "$cardWhere: ${card.pages.size} pages"
                }
                require(card.contentUpdatedAt >= 0 && card.srsUpdatedAt >= 0 && (card.deletedAt ?: 0) >= 0) {
                    "$cardWhere: negative timestamp"
                }
                val srs = card.srsState
                val difficultyRange = SrsConstants.DIFFICULTY_MIN..SrsConstants.DIFFICULTY_MAX
                require(srs.difficulty.isFinite() && srs.difficulty in difficultyRange) {
                    "$cardWhere: difficulty ${srs.difficulty}"
                }
                require(srs.stability.isFinite() && srs.stability >= 0 && srs.repetitions >= 0) {
                    "$cardWhere: stability ${srs.stability}, repetitions ${srs.repetitions}"
                }
                require(srs.state in SrsStates.VALID && srs.lastReviewTimestamp >= 0 && srs.nextReviewTimestamp >= 0) {
                    "$cardWhere: SRS state ${srs.state} or negative review time"
                }
            }
        }
        data.studyModes.forEach { mode ->
            val where = "mode ${mode.id}"
            require(mode.name.isNotBlank() && mode.updatedAt >= 0 && (mode.deletedAt ?: 0) >= 0) {
                "$where: blank name or negative timestamp"
            }
            if (mode.isBuiltIn) {
                id(requireNotNull(mode.builtInSourceId) { "$where: built-in without source id" })
            } else {
                require(mode.builtInSourceId == null) { "$where: custom mode with source id" }
            }
            mode.steps.forEach { require(normalizeModeStep(it) == it) { "$where: step out of range: $it" } }
            CompoundExpander.expandModeSteps(mode.steps).forEach { step ->
                step.referencedPage?.let {
                    require(it in 0 until PageLimits.MAX_STORED_PAGE_COUNT) { "$where: page $it out of range" }
                }
            }
        }
        data.activityHeatmap.forEach { (day, count) ->
            require(runCatching { LocalDate.parse(day).toString() }.getOrNull() == day && count >= 0) {
                "activity day '$day' with count $count"
            }
        }
    }
}
