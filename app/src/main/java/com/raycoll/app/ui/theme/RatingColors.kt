package com.raycoll.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RatingColors(
    val againBg: Color,
    val againFg: Color,
    val hardBg: Color,
    val hardFg: Color,
    val goodBg: Color,
    val goodFg: Color,
    val easyBg: Color,
    val easyFg: Color
)

val LightRatingColors = RatingColors(
    LightErrorContainer,
    LightOnErrorContainer,
    SrsLearningBgLight,
    SrsLearningFgLight,
    LightPrimaryContainer,
    LightOnPrimaryContainer,
    SrsMasteredBgLight,
    SrsMasteredFgLight
)
val DarkRatingColors = RatingColors(
    DarkErrorContainer,
    DarkOnErrorContainer,
    SrsLearningBgDark,
    SrsLearningFgDark,
    DarkPrimaryContainer,
    DarkOnPrimaryContainer,
    SrsMasteredBgDark,
    SrsMasteredFgDark
)
val LocalRatingColors = staticCompositionLocalOf { LightRatingColors }
