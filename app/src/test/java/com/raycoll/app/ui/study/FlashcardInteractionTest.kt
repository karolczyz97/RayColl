package com.raycoll.app.ui.study

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class FlashcardInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions = CardActions(
        onTap = { events += "tap" },
        onPressDown = { events += "down" },
        onPressUp = { events += "up" },
        onCancelPress = { events += "cancel" },
        onRate = { events += "rate" },
        onPrevious = { events += "previous" },
        onNext = { events += "next" }
    )
    private fun face() = CardFace(
        card = Flashcard("card", listOf("question", "answer", "example"), SrsState()),
        pages = listOf("question", "answer", "example"),
        revealedPages = listOf(0),
        peekedPage = null,
        audioPage = null,
        listening = false
    )

    @Test
    fun `tap still releases the press`() {
        compose.setContent { FlashcardView(face(), actions, Modifier) }
        compose.onRoot().performTouchInput { click() }
        assertEquals(listOf("down", "up"), events)
    }

    @Test
    fun `cancelled press does not turn into a tap`() {
        compose.setContent { FlashcardView(face(), actions, Modifier) }
        compose.onRoot().performTouchInput {
            down(center)
            cancel()
        }
        assertEquals(listOf("down", "cancel"), events)
    }

    @Test
    fun `a passive preview never triggers card actions`() {
        compose.setContent { FlashcardView(face(), actions, Modifier, interactive = false) }
        compose.onRoot().performTouchInput { click() }
        assertEquals(emptyList<String>(), events)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `all page dividers remain visible before and during reveal`() {
        var shown by mutableStateOf(face())
        var divider = Color.Unspecified
        compose.setContent {
            divider = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
            FlashcardView(shown, actions, Modifier)
        }
        fun dividerCount(): Int {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] == divider }
            return column.zipWithNext().count { (before, after) -> !before && after }
        }
        assertEquals(2, dividerCount())
        compose.onNodeWithText("answer").assertDoesNotExist()
        compose.onNodeWithText("example").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = shown.copy(revealedPages = listOf(0, 1)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        assertEquals(2, dividerCount())
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(2, dividerCount())
    }
}
