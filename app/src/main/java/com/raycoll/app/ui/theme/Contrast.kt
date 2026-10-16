package com.raycoll.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Zachowuje odcień wykresu, przyciemniając lub rozjaśniając go tylko do wymaganego kontrastu. */
internal fun Color.withMinimumContrast(background: Color, minimum: Float = 3f): Color {
    val visible = compositeOver(background)
    if (contrastRatio(visible, background) >= minimum) return this
    val target = if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2f
        if (contrastRatio(lerp(visible, target, middle), background) >= minimum) high = middle else low = middle
    }
    return lerp(visible, target, high)
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (maxOf(firstLuminance, secondLuminance) + 0.05f) / (minOf(firstLuminance, secondLuminance) + 0.05f)
}
