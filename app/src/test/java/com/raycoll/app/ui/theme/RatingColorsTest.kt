package com.raycoll.app.ui.theme

import androidx.compose.ui.graphics.luminance
import com.raycoll.app.data.local.prefs.ThemePref
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingColorsTest {
    @Test
    fun `all fixed rating palettes keep readable contrast`() {
        for (palette in listOf(LightRatingColors, DarkRatingColors)) {
            for ((bg, fg) in listOf(
                palette.againBg to palette.againFg,
                palette.hardBg to palette.hardFg,
                palette.goodBg to palette.goodFg,
                palette.easyBg to palette.easyFg
            )) {
                val contrast = (maxOf(bg.luminance(), fg.luminance()) + 0.05) /
                    (minOf(bg.luminance(), fg.luminance()) + 0.05)
                assertTrue("Text contrast $contrast", contrast >= 4.5)
            }
        }
    }

    @Test
    fun `explicit theme wins over system and system preference follows it`() {
        assertFalse(ThemePref.LIGHT.resolveDarkMode(true))
        assertTrue(ThemePref.DARK.resolveDarkMode(false))
        assertFalse(ThemePref.SYSTEM.resolveDarkMode(false))
        assertTrue(ThemePref.SYSTEM.resolveDarkMode(true))
    }
}
