package com.raycoll.app.ui.study

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.session.CardDestination
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class StudyPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val deps = FakeSessionDeps()
    private lateinit var engine: SessionEngine
    private val destinations = mutableListOf<CardDestination?>()
    private var taps = 0

    private fun showCards(count: Int = 3, firstAnswer: String = "a1") {
        val cards = (1..count).map {
            Flashcard("c$it", listOf("q$it", if (it == 1) firstAnswer else "a$it"), SrsState())
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            engine = remember {
                SessionEngine(
                    FlashcardGroup("deck", "Deck", cards = emptyList(), activePageCount = 2),
                    listOf(ShowPageStep(), WaitForTapToRevealStep(), ShowRatingsStep()),
                    deps,
                    FakeClock(),
                    1f,
                    scope
                )
            }
            LaunchedEffect(engine) { engine.start(cards) }
            val session by engine.state.collectAsState()
            val actions = remember {
                CardActions(
                    onTap = {
                        taps++
                        engine.handleCardPress()
                    },
                    onPressDown = engine::pressDown,
                    onPressUp = engine::pressUp,
                    onCancelPress = engine::cancelPress,
                    onRate = engine::rate,
                    onPrevious = { engine.goToPreviousCard() },
                    onNext = engine::skipToNextCard,
                    onNavigationStart = engine::beginNavigation,
                    onNavigationContinue = engine::continueNavigation,
                    onNavigationEnd = { navigation, destination ->
                        destinations += destination
                        engine.endNavigation(navigation, destination)
                        val s = engine.state.value
                        if (s.status == SessionStatus.FINISHED) s.cards.size else s.currentCardIndex
                    }
                )
            }
            if (session.status == SessionStatus.FINISHED) {
                Text("finished")
            } else {
                StudyCardContent(
                    StudyUiState.Active("Deck", session, 2, false, false, false),
                    actions
                )
            }
        }
        compose.waitForIdle()
    }

    private fun left() {
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.85f, 50f), Offset(width * 0.15f, 50f), 300)
        }
        compose.waitForIdle()
    }

    private fun right() {
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.15f, 50f), Offset(width * 0.85f, 50f), 300)
        }
        compose.waitForIdle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `next question is visible during dragging before the session changes card`() {
        showCards()
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.85f, 50f))
            moveBy(Offset(-width * 0.6f, 0f), 400)
        }
        compose.waitForIdle()
        assertEquals(0, engine.state.value.currentCardIndex)
        compose.onNodeWithText("q2", useUnmergedTree = true).assertIsDisplayed()
        val preview = File("build/outputs/ui-previews/study-pager-half.png")
        preview.parentFile?.mkdirs()
        preview.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(destinations.isEmpty())
        assertTrue(deps.reviews.isEmpty())
        compose.onRoot().performTouchInput {
            moveBy(Offset(-width * 0.2f, 0f), 200)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, engine.state.value.currentCardIndex)
        assertEquals(listOf(CardDestination.Card("c2")), destinations)
        assertEquals(0, taps)
    }

    @Test
    fun `an interrupted gesture returns to the same revealed card without a grade`() {
        showCards()
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.onNodeWithText("a1").assertIsDisplayed()
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.85f, 50f))
            moveBy(Offset(-width * 0.6f, 0f), 400)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Good").assertIsNotEnabled()
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(0, engine.state.value.currentCardIndex)
        assertEquals(listOf(0, 1), engine.state.value.revealedPages)
        compose.onNodeWithText("a1").assertIsDisplayed()
        compose.onNodeWithText("Good").assertIsEnabled()
        assertTrue(deps.reviews.isEmpty())
    }

    @Test
    fun `previous revealed answer is visible in the incoming preview and after returning`() {
        showCards()
        compose.onNodeWithText("Tap to reveal").performClick()
        left()
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.15f, 50f))
            moveBy(Offset(width * 0.55f, 0f), 400)
        }
        compose.waitForIdle()
        assertEquals(1, engine.state.value.currentCardIndex)
        compose.onNodeWithText("a1", useUnmergedTree = true).assertIsDisplayed()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0, engine.state.value.currentCardIndex)
        assertEquals(listOf(0, 1), engine.state.value.revealedPages)
        compose.onNodeWithText("a1").assertIsDisplayed()
        assertTrue(deps.reviews.isEmpty())
    }

    @Test
    fun `rating-driven scrolling does not generate another navigation or review`() {
        showCards()
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.onNodeWithText("Good").performClick()
        compose.waitForIdle()
        assertEquals(1, engine.state.value.currentCardIndex)
        assertEquals(1, deps.reviews.size)
        assertTrue(destinations.isEmpty())
        right()
        compose.onNodeWithText("a1").assertIsDisplayed()
        assertEquals(1, deps.reviews.size)
        compose.onNodeWithText("Easy").performClick()
        compose.waitForIdle()
        assertEquals(1, engine.state.value.currentCardIndex)
        assertEquals(2, deps.reviews.size)
        assertNotNull(deps.reviews.last().replaceFromBase)
    }

    @Test
    fun `consecutive left and right swipes each change exactly one card`() {
        showCards()
        left()
        left()
        assertEquals(2, engine.state.value.currentCardIndex)
        right()
        right()
        assertEquals(0, engine.state.value.currentCardIndex)
        assertEquals(4, destinations.size)
        right()
        assertEquals(0, engine.state.value.currentCardIndex)
        assertTrue(deps.reviews.isEmpty())
    }

    @Test
    fun `button-driven transitions enter from the matching side`() {
        showCards()
        val restingLeft = compose.onNodeWithText("q1").getUnclippedBoundsInRoot().left
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { engine.skipToNextCard() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(96)
        assertTrue(compose.onNodeWithText("q2", useUnmergedTree = true).getUnclippedBoundsInRoot().left > restingLeft)
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { engine.goToPreviousCard() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(96)
        assertTrue(compose.onNodeWithText("q1", useUnmergedTree = true).getUnclippedBoundsInRoot().left < restingLeft)
        compose.mainClock.advanceTimeBy(2_000)
        assertTrue(destinations.isEmpty())
    }

    @Test
    fun `swiping beyond the last card opens summary without adding a fake card or rating`() {
        showCards(count = 1)
        left()
        compose.onNodeWithText("finished").assertIsDisplayed()
        assertEquals(SessionStatus.FINISHED, engine.state.value.status)
        assertEquals(1, engine.state.value.cards.size)
        assertEquals(listOf(CardDestination.Summary), destinations)
        assertTrue(deps.reviews.isEmpty())
    }

    @Test
    fun `vertical position of a revealed card survives leaving the pager cache and returning`() {
        showCards(count = 4, firstAnswer = (1..40).joinToString("\n") { "Definition line $it" })
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("q1", useUnmergedTree = true).performScrollTo()
        compose.onRoot().performTouchInput {
            swipe(Offset(centerX, height * 0.4f), Offset(centerX, height * 0.1f), 400)
        }
        compose.waitForIdle()
        fun scrollPosition(): Float = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = scrollPosition()
        assertTrue(before > 0f)
        repeat(3) { left() }
        compose.onNodeWithText("q1", useUnmergedTree = true).assertDoesNotExist()
        repeat(3) { right() }
        assertEquals(0, engine.state.value.currentCardIndex)
        assertEquals(listOf(0, 1), engine.state.value.revealedPages)
        assertEquals(before, scrollPosition(), 1f)
    }

    @Test
    fun `two quick swipes while the first is still settling do not bounce back to the original card`() {
        showCards()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.85f, 50f), Offset(width * 0.15f, 50f), 150)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48)
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.85f, 50f), Offset(width * 0.15f, 50f), 150)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertEquals(2, engine.state.value.currentCardIndex)
        assertTrue(deps.reviews.isEmpty())
    }
}
