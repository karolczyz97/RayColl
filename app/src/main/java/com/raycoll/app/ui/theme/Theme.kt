package com.raycoll.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandLightScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = BrandOnSecondary,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
)

private val BrandDarkScheme = darkColorScheme(
    primary = Color(0xFFC9BFFF),
    onPrimary = Color(0xFF2900A6),
    primaryContainer = Color(0xFF3F1FB6),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC8C3DD),
    onSecondary = Color(0xFF302D40),
    secondaryContainer = Color(0xFF474458),
    onSecondaryContainer = Color(0xFFE4DFF9),
)

@Composable
fun RayCollTheme(
    useDynamicColors: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BrandDarkScheme
        else -> BrandLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RayCollTypography,
        shapes = RayCollShapes,
        content = content
    )
}
