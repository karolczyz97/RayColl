package com.raycoll.app.domain.srs

import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.model.SrsState

enum class SrsCardCategory { NEW, LEARNING, REVIEW, MASTERED }

fun getCardCategory(state: SrsState): SrsCardCategory = when (state.state) {
    0 -> SrsCardCategory.NEW
    1, 3 -> SrsCardCategory.LEARNING
    2 -> if (state.repetitions >= SrsConstants.MASTERED_MIN_REPS) SrsCardCategory.MASTERED else SrsCardCategory.REVIEW
    else -> SrsCardCategory.NEW
}
