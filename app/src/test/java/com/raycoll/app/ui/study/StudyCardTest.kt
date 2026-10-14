package com.raycoll.app.ui.study

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.domain.session.SessionAction
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.sessionReducer
import com.raycoll.app.ui.theme.LocalSrsColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class StudyCardTest {

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `progress bar retains visible card segments with 150 cards`() {
        val session =
            SessionState(currentCardIndex = 75, cards = List(150) { Flashcard("c$it", listOf("q", "a"), SrsState()) })
        var currentColor = Color.Unspecified
        compose.setContent {
            currentColor = LocalSrsColors.current.newBg
            SessionProgressBar(session)
        }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertTrue(
            (0 until pixels.height).any { y ->
                (0 until pixels.width / 2).any { x -> pixels[x, y] == currentColor }
            }
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val noActions = CardActions({}, {}, {}, {}, {}, {}, {})

    private fun active(session: SessionState, activePageCount: Int) = StudyUiState.Active(
        deckName = "Deck",
        session = session,
        activePageCount = activePageCount,
        runsInBackground = false,
        needsMicrophone = false,
        endedEarly = false
    )

    @Test
    fun `reveal button counts only active pages of the deck`() {
        val card = Flashcard(id = "c1", pages = listOf("front", "back", "hidden example"), srsState = SrsState())
        var state by mutableStateOf(active(SessionState(cards = listOf(card), revealedPages = listOf(0)), 2))
        compose.setContent { StudyCardContent("deck", state, noActions) }

        compose.onNodeWithText("Tap to reveal").assertExists()

        state = active(state.session.copy(revealedPages = listOf(0, 1)), 2)
        compose.waitForIdle()
        compose.onNodeWithText("Tap to reveal").assertDoesNotExist()
        compose.onNodeWithText("back").assertExists()
        compose.onNodeWithText("hidden example").assertDoesNotExist()
    }

    @Test
    fun `long page text is shown in full and can be scrolled to`() {
        val definition = (1..15).joinToString("\n") { "Line $it of a long definition" }
        val card = Flashcard(id = "c1", pages = listOf("term", definition), srsState = SrsState())
        val state = active(SessionState(cards = listOf(card), revealedPages = listOf(0, 1)), 2)
        compose.setContent { StudyCardContent("deck", state, noActions) }

        val node = compose.onNodeWithText(definition, useUnmergedTree = true)
        node.performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        // wszystkie linie złożone, nic nie ucięte w pionie (wcześniej maxLines = 10 + wielokropek)
        val layout = layouts.single()
        assertEquals(15, layout.lineCount)
        assertFalse(layout.didOverflowHeight)
    }

    @Test
    fun `answer of the previous card does not stay under the new question`() {
        val cards = listOf(
            Flashcard(id = "c1", pages = listOf("q1", "a1"), srsState = SrsState()),
            Flashcard(id = "c2", pages = listOf("q2", "a2"), srsState = SrsState())
        )
        val answered = SessionState(
            cards = cards,
            revealedPages = listOf(0, 1),
            lastAnswerResult = LastAnswerResult(status = AnswerStatus.SKIPPED)
        )
        var state by mutableStateOf(active(answered, 2))
        compose.setContent { StudyCardContent("deck", state, noActions) }
        compose.onNodeWithText("Skipped", substring = true).assertExists()

        state = active(sessionReducer(answered, SessionAction.AdvanceCard(1)), 2)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithText("q2").assertExists()
        compose.onNodeWithText("Skipped", substring = true).assertDoesNotExist()
        // nowa karta może być odkryta — stary wynik niczego nie blokuje
        compose.onNodeWithText("Tap to reveal").assertExists()
    }
}
