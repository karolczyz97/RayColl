package com.raycoll.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class SrsCardCategory { New, Learning, Review, Mastered }

data class SrsStatusColors(val foreground: Color, val background: Color)

object SrsColors {
    fun forCategory(category: SrsCardCategory, isDark: Boolean): SrsStatusColors = when (category) {
        SrsCardCategory.New -> if (isDark) SrsStatusColors(DarkSrsNewColor, DarkSrsNewBackground)
            else SrsStatusColors(SrsNewColor, SrsNewBackground)
        SrsCardCategory.Learning -> if (isDark) SrsStatusColors(DarkSrsLearningColor, DarkSrsLearningBackground)
            else SrsStatusColors(SrsLearningColor, SrsLearningBackground)
        SrsCardCategory.Review -> if (isDark) SrsStatusColors(DarkSrsReviewColor, DarkSrsReviewBackground)
            else SrsStatusColors(SrsReviewColor, SrsReviewBackground)
        SrsCardCategory.Mastered -> if (isDark) SrsStatusColors(DarkSrsMasteredColor, DarkSrsMasteredBackground)
            else SrsStatusColors(SrsMasteredColor, SrsMasteredBackground)
    }

    val categoryOrder: List<SrsCardCategory> = listOf(
        SrsCardCategory.New, SrsCardCategory.Learning, SrsCardCategory.Review, SrsCardCategory.Mastered
    )

    fun heatmapColor(primary: Color, count: Int): Color = when {
        count <= 0 -> Color.LightGray
        count <= 2 -> primary.copy(alpha = 0.25f)
        count <= 5 -> primary.copy(alpha = 0.5f)
        count <= 10 -> primary.copy(alpha = 0.75f)
        else -> primary
    }
}
