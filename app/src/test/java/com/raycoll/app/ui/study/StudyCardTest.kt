package com.raycoll.app.ui.study

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.sp
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.domain.session.SessionAction
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.domain.session.sessionReducer
import com.raycoll.app.ui.theme.LocalSrsColors
import com.raycoll.app.ui.theme.withMinimumContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class StudyCardTest {
    @Test
    fun `reveal label retains its content until the exit animation finishes`() {
        var state by mutableStateOf(active(SessionState(cards = listOf(twoPageCard)), 2))
        compose.setContent { StudyCardContent(state, noActions) }
        compose.onNodeWithText("Tap to reveal").assertExists()
        compose.mainClock.autoAdvance = false
        state = state.copy(session = state.session.copy(status = SessionStatus.LISTENING))
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Tap to reveal").assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Tap to reveal").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `a late answer panel never pulls a manually scrolled card back down`() {
        val front = "Question"
        val card = twoPageCard.copy(pages = listOf(front, (1..40).joinToString("\n") { "Definition line $it" }))
        var state by mutableStateOf(active(SessionState(cards = listOf(card), revealedPages = listOf(0, 1)), 2))
        compose.setContent { StudyCardContent(state, noActions) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText(front, useUnmergedTree = true).performScrollTo()
        fun position(): Float = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            useUnmergedTree = true
        ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = position()
        state = state.copy(
            session = state.session.copy(
                lastAnswerResult = LastAnswerResult(AnswerStatus.ERROR, text = "Recognized text ".repeat(100))
            )
        )
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(before, position(), 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `progress bar retains visible card segments with 150 cards`() {
        val session =
            SessionState(currentCardIndex = 75, cards = List(150) { Flashcard("c$it", listOf("q", "a"), SrsState()) })
        var currentColor = Color.Unspecified
        compose.setContent {
            currentColor = LocalSrsColors.current.newAccent.withMinimumContrast(
                androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                4.5f
            )
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
        compose.setContent { StudyCardContent(state, noActions) }

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
        compose.setContent { StudyCardContent(state, noActions) }

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
            lastAnswerResult = LastAnswerResult(status = AnswerStatus.INCORRECT, text = "old answer")
        )
        var state by mutableStateOf(active(answered, 2))
        compose.setContent { StudyCardContent(state, noActions) }
        compose.onNodeWithText("old answer", substring = true).assertExists()

        state = active(sessionReducer(answered, SessionAction.AdvanceCard(1)), 2)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithText("q2").assertExists()
        compose.onNodeWithText("old answer", substring = true).assertDoesNotExist()
        // nowa karta może być odkryta — stary wynik niczego nie blokuje
        compose.onNodeWithText("Tap to reveal").assertExists()
    }

    private fun pageState(label: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)

    private val twoPageCard = Flashcard(id = "c1", pages = listOf("front", "back"), srsState = SrsState())

    private fun audioState(status: SessionStatus, page: Int?) = active(
        SessionState(
            cards = listOf(twoPageCard),
            revealedPages = listOf(0, 1),
            status = status,
            audioPageIndex = page
        ),
        2
    )

    private fun pageBounds(): Pair<DpRect, DpRect> = Pair(
        compose.onNodeWithText("front", useUnmergedTree = true).getUnclippedBoundsInRoot(),
        compose.onNodeWithText("back", useUnmergedTree = true).getUnclippedBoundsInRoot()
    )

    private fun assertAudioNeverMovesText() {
        var state by mutableStateOf(audioState(SessionStatus.IDLE, null))
        compose.setContent { StudyCardContent(state, noActions) }
        val idle = pageBounds()
        listOf(
            SessionStatus.SPEAKING to 0,
            SessionStatus.SPEAKING to 1,
            SessionStatus.LISTENING to 1,
            SessionStatus.IDLE to null
        ).forEach { (status, page) ->
            state = audioState(status, page)
            compose.waitForIdle()
            assertEquals("$status/$page", idle, pageBounds())
        }
    }

    @Test
    fun `audio indicator never moves page text`() = assertAudioNeverMovesText()

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `audio indicator never moves page text in landscape`() = assertAudioNeverMovesText()

    @Test
    fun `audio indicator is exposed to TalkBack`() {
        var state by mutableStateOf(audioState(SessionStatus.SPEAKING, 0))
        compose.setContent { StudyCardContent(state, noActions) }
        compose.onNode(pageState("Speaking")).assertExists()

        state = audioState(SessionStatus.LISTENING, 1)
        compose.waitForIdle()
        compose.onNode(pageState("Listening")).assertExists()
    }

    @Test
    fun `audio indicator is held briefly and then removed`() {
        var state by mutableStateOf(audioState(SessionStatus.SPEAKING, 0))
        compose.setContent { StudyCardContent(state, noActions) }
        compose.onNode(pageState("Speaking")).assertExists()

        compose.mainClock.autoAdvance = false
        state = audioState(SessionStatus.IDLE, null)
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(pageState("Speaking")).assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(pageState("Speaking")).assertDoesNotExist()
    }

    @Test
    fun `page font size shrinks with length and in landscape`() {
        assertEquals(28.sp, pageFontSize("a".repeat(70), compact = false))
        assertEquals(24.sp, pageFontSize("a".repeat(71), compact = false))
        assertEquals(24.sp, pageFontSize("a".repeat(140), compact = false))
        assertEquals(20.sp, pageFontSize("a".repeat(141), compact = false))
        assertEquals(20.sp, pageFontSize("a".repeat(260), compact = false))
        assertEquals(18.sp, pageFontSize("a".repeat(261), compact = false))
        assertEquals(28.sp * 0.72f, pageFontSize("a", compact = true))
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `rating buttons stay readable at large font scale`() {
        compose.setContent { RatingButtons(onRate = {}, enabled = true) }
        listOf("Again", "Hard", "Good", "Easy").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `session summary buttons are reachable in landscape`() {
        val cards = List(3) { Flashcard("c$it", listOf("q", "a"), SrsState()) }
        val state = StudyUiState.Active(
            deckName = "Deck",
            session = SessionState(status = SessionStatus.FINISHED, cards = cards, failedCardIds = setOf("c0")),
            activePageCount = 2,
            runsInBackground = false,
            needsMicrophone = false,
            endedEarly = false,
            resultCards = cards,
            reviewedCardCount = 3,
            completedCardCount = 3
        )
        compose.setContent { SessionSummary(state, {}, {}, {}) }
        compose.onNodeWithText("Back to dashboard").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `answer panel is announced only when not listening`() {
        val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        var listening by mutableStateOf(false)
        compose.setContent {
            AnswerPanel(LastAnswerResult(status = AnswerStatus.CORRECT, text = "kot"), listening = listening, onTap = {
            })
        }
        compose.onAllNodes(live).assertCountEquals(1)
        listening = true
        compose.waitForIdle()
        compose.onAllNodes(live).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `newly revealed page of a tall card is scrolled into view`() {
        val tall = (1..14).joinToString("\n") { "Line $it" }
        val card = Flashcard(id = "c1", pages = listOf("front", tall, "third page"), srsState = SrsState())
        var state by mutableStateOf(active(SessionState(cards = listOf(card), revealedPages = listOf(0, 1)), 3))
        compose.setContent { StudyCardContent(state, noActions) }
        compose.waitForIdle()

        state = active(state.session.copy(revealedPages = listOf(0, 1, 2)), 3)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("third page", useUnmergedTree = true).assertIsDisplayed()
    }
}
