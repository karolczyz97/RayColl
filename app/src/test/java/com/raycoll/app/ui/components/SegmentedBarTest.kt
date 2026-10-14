package com.raycoll.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.raycoll.app.domain.selectors.CardStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SegmentedBarTest {

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
