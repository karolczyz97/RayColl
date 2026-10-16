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
    /** Kolory pasków i wykresów; [newFg] itd. są tylko do tekstu na tle [newBg] itd. */
    val newAccent: Color,
    val learningAccent: Color,
    val reviewAccent: Color,
    val masteredAccent: Color
)

val LightSrsColors = SrsColors(
    newFg = SrsNewFgLight,
    newBg = SrsNewBgLight,
    learningFg = SrsLearningFgLight,
    learningBg = SrsLearningBgLight,
    reviewFg = SrsReviewFgLight,
    reviewBg = SrsReviewBgLight,
    masteredFg = SrsMasteredFgLight,
    masteredBg = SrsMasteredBgLight,
    newAccent = SrsNewAccentLight,
    learningAccent = SrsLearningAccentLight,
    reviewAccent = SrsReviewAccentLight,
    masteredAccent = SrsMasteredAccentLight
)

val DarkSrsColors = SrsColors(
    newFg = SrsNewFgDark,
    newBg = SrsNewBgDark,
    learningFg = SrsLearningFgDark,
    learningBg = SrsLearningBgDark,
    reviewFg = SrsReviewFgDark,
    reviewBg = SrsReviewBgDark,
    masteredFg = SrsMasteredFgDark,
    masteredBg = SrsMasteredBgDark,
    newAccent = SrsNewFgDark,
    learningAccent = SrsLearningFgDark,
    reviewAccent = SrsReviewFgDark,
    masteredAccent = SrsMasteredFgDark
)
