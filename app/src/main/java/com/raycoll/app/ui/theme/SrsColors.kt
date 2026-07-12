package com.raycoll.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SrsColors(
    val newFg: Color,
    val newBg: Color,
    val learningFg: Color,
    val learningBg: Color,
    val reviewFg: Color,
    val reviewBg: Color,
    val masteredFg: Color,
    val masteredBg: Color,
)

val LightSrsColors = SrsColors(
    newFg = SrsNewFg, newBg = SrsNewBg,
    learningFg = SrsLearningFg, learningBg = SrsLearningBg,
    reviewFg = SrsReviewFg, reviewBg = SrsReviewBg,
    masteredFg = SrsMasteredFg, masteredBg = SrsMasteredBg,
)

val DarkSrsColors = SrsColors(
    newFg = SrsNewBg, newBg = SrsNewFg,
    learningFg = SrsLearningBg, learningBg = SrsLearningFg,
    reviewFg = SrsReviewBg, reviewBg = SrsReviewFg,
    masteredFg = SrsMasteredBg, masteredBg = SrsMasteredFg,
)
