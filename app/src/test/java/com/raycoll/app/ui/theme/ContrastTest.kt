package com.raycoll.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun `chart colors retain sufficient contrast on light dark and wallpaper surfaces`() {
        val backgrounds =
            listOf(LightSurfaceContainerHighest, DarkSurfaceContainerHighest, Color(0xFFD9E5DA), Color(0xFF303A31))
        val colors = listOf(LightSrsColors, DarkSrsColors).flatMap {
            listOf(it.newAccent, it.learningAccent, it.reviewAccent, it.masteredAccent)
        }
        for (background in backgrounds) {
            for (color in colors) {
                for (alpha in listOf(0.22f, 0.35f, 0.6f, 0.65f, 1f)) {
                    val adjusted = color.copy(alpha = alpha).withMinimumContrast(background)
                    assertTrue(
                        "$color / $background at $alpha",
                        contrastRatio(adjusted.compositeOver(background), background) >= 3f
                    )
                }
            }
        }
    }

    @Test
    fun `contrasting colors are preserved`() {
        assertEquals(SrsNewAccentLight, SrsNewAccentLight.withMinimumContrast(LightSurfaceContainerHighest))
        assertEquals(Color.White, Color.White.withMinimumContrast(Color.Black))
    }

    @Test
    fun `progress shades stay distinct and readable`() {
        for (background in listOf(LightSurfaceContainerHighest, DarkSurfaceContainerHighest)) {
            val color = SrsLearningAccentLight
            val ratios = listOf(3f, 3.75f, 4.5f).map { minimum ->
                contrastRatio(color.withMinimumContrast(background, minimum).compositeOver(background), background)
            }
            assertTrue(ratios.zipWithNext().all { (before, after) -> after >= before })
            assertTrue(ratios.last() >= 4.5f)
        }
    }
}
