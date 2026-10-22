package com.raycoll.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import com.raycoll.app.data.local.prefs.ThemePref
import com.raycoll.app.domain.selectors.CardStats
import com.raycoll.app.ui.theme.LightSrsColors
import com.raycoll.app.ui.theme.RayCollTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class SegmentedBarTest {

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `category bars render the same pastels in both themes without darkening them`() {
        var theme by mutableStateOf(ThemePref.LIGHT)
        compose.setContent {
            RayCollTheme(theme = theme, useSystemColors = false) {
                CardStatsBar(CardStats(total = 4, newCount = 1, learning = 1, review = 1, mastered = 1))
            }
        }
        val pastels = with(LightSrsColors) { listOf(newAccent, learningAccent, reviewAccent, masteredAccent) }
        listOf(ThemePref.LIGHT, ThemePref.DARK).forEach { selected ->
            theme = selected
            compose.waitForIdle()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            pastels.forEachIndexed { index, color ->
                assertEquals(
                    "$selected category $index",
                    color,
                    pixels[pixels.width * (index * 2 + 1) / 8, pixels.height / 2]
                )
            }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `deck stats bar describes its segments for TalkBack`() {
        compose.setContent {
            CardStatsBar(CardStats(total = 10, newCount = 4, learning = 3, review = 2, mastered = 1))
        }
        compose.onNodeWithContentDescription("New (4), Learning (3), Review (2), Mastered (1)").assertExists()
    }
}
