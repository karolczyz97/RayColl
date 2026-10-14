package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.SrsStates

enum class SrsCardCategory { NEW, LEARNING, REVIEW, MASTERED }

fun getCardCategory(state: SrsState): SrsCardCategory = when (state.state) {
    SrsStates.NEW -> SrsCardCategory.NEW

    SrsStates.LEARNING, SrsStates.RELEARNING -> SrsCardCategory.LEARNING

    SrsStates.REVIEW ->
        if (state.repetitions >= SrsConstants.MASTERED_MIN_REPS) SrsCardCategory.MASTERED else SrsCardCategory.REVIEW

    else -> SrsCardCategory.NEW
}
