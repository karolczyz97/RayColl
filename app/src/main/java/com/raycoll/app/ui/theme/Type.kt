package com.raycoll.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseTypography = Typography()

private fun TextStyle.sized(size: Int, weight: FontWeight): TextStyle = copy(
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = (lineHeight.value * size / fontSize.value).sp
)

val RayCollTypography = baseTypography.copy(
    headlineLarge = baseTypography.headlineLarge.sized(28, FontWeight.Bold),
    headlineMedium = baseTypography.headlineMedium.sized(24, FontWeight.SemiBold),
    headlineSmall = baseTypography.headlineSmall.sized(20, FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.sized(20, FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.sized(16, FontWeight.Medium),
    titleSmall = baseTypography.titleSmall.sized(14, FontWeight.Medium),
    bodyLarge = baseTypography.bodyLarge.sized(16, FontWeight.Normal),
    bodyMedium = baseTypography.bodyMedium.sized(14, FontWeight.Normal),
    bodySmall = baseTypography.bodySmall.sized(12, FontWeight.Normal),
    labelLarge = baseTypography.labelLarge.sized(14, FontWeight.Medium),
    labelMedium = baseTypography.labelMedium.sized(12, FontWeight.Medium),
    labelSmall = baseTypography.labelSmall.sized(11, FontWeight.Medium)
)
