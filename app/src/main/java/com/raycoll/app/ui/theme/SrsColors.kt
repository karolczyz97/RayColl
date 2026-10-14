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
    val masteredBg: Color
)

val LightSrsColors = SrsColors(
    newFg = SrsNewFgLight,
    newBg = SrsNewBgLight,
    learningFg = SrsLearningFgLight,
    learningBg = SrsLearningBgLight,
    reviewFg = SrsReviewFgLight,
    reviewBg = SrsReviewBgLight,
    masteredFg = SrsMasteredFgLight,
    masteredBg = SrsMasteredBgLight
)

val DarkSrsColors = SrsColors(
    newFg = SrsNewFgDark,
    newBg = SrsNewBgDark,
    learningFg = SrsLearningFgDark,
    learningBg = SrsLearningBgDark,
    reviewFg = SrsReviewFgDark,
    reviewBg = SrsReviewBgDark,
    masteredFg = SrsMasteredFgDark,
    masteredBg = SrsMasteredBgDark
)
