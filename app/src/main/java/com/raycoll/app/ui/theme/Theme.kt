package com.raycoll.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandSeed,
    secondary = Color(0xFF7C70E5),
    tertiary = Color(0xFF9F8AE0),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BEFF),
    secondary = Color(0xFF9F8AE0),
    tertiary = Color(0xFF7C70E5),
)

val LocalSrsColors = staticCompositionLocalOf { LightSrsColors }

enum class ThemePref { LIGHT, DARK, SYSTEM }

@Composable
fun RayCollTheme(
    themePref: String = "system",
    useSystemColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = when (themePref) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        useSystemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColors
        else -> LightColors
    }

    val srsColors = if (isDark) DarkSrsColors else LightSrsColors

    CompositionLocalProvider(LocalSrsColors provides srsColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RayCollTypography,
            shapes = RayCollShapes,
            content = content,
        )
    }
}
