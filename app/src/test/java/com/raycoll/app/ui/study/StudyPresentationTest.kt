package com.raycoll.app.ui.study

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.Ratings
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.theme.RayCollTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val card = Flashcard("c", listOf("Question", "Answer"), SrsState())
    private fun active(session: SessionState) = StudyUiState.Active("Deck", session, 2, false, false, false)

    @Test
    fun `summary geometry stays fixed while final card statistics arrive`() {
        val session = SessionState(cards = listOf(card), status = SessionStatus.FINISHED)
        var state by mutableStateOf(active(session).copy(completedCardCount = 1, reviewedCardCount = 1))
        compose.setContent {
            RayCollTheme(useSystemColors = false) {
                Box(Modifier.width(360.dp).height(700.dp)) { SessionSummary(state, {}, {}, {}) }
            }
        }
        val title = context.getString(R.string.study_bravo)
        val counter = context.getString(R.string.study_summary_cards_done, 1, 1)
        val before = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val counterBefore = compose.onNodeWithText(counter).getUnclippedBoundsInRoot()
        state = state.copy(resultCards = listOf(card))
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithText(title).getUnclippedBoundsInRoot())
        assertEquals(counterBefore, compose.onNodeWithText(counter).getUnclippedBoundsInRoot())
    }

    @Test
    fun `suggested rating is a hint without selecting or saving and clears on the next card`() {
        var suggested by mutableStateOf<Int?>(Ratings.GOOD)
        var selected: Int? = null
        compose.setContent { RatingButtons(onRate = { selected = it }, enabled = true, suggestedRating = suggested) }
        val label = context.getString(ratingLabel(Ratings.GOOD))
        val suggestion = context.getString(R.string.study_suggested_rating, label)
        compose.onNodeWithText(label).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, suggestion)
        )
        assertNull(selected)
        suggested = null
        compose.onNodeWithText(label).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `full screen empty action remains reachable in a low window with a large font`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(360.dp).height(180.dp)) {
                    EmptyState("A long explanation that wraps across several lines.", action = {
                        Button(onClick = {}) { Text("Continue") }
                    })
                }
            }
        }
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `screen phases do not change for partial speech updates but do change for the summary`() {
        val state = active(SessionState(cards = listOf(card), status = SessionStatus.LISTENING))
        assertEquals(StudyScreenPhase.ACTIVE, state.screenPhase())
        assertEquals(
            state.screenPhase(),
            state.copy(session = state.session.copy(status = SessionStatus.SPEAKING)).screenPhase()
        )
        assertEquals(
            StudyScreenPhase.SUMMARY,
            state.copy(session = state.session.copy(status = SessionStatus.FINISHED)).screenPhase()
        )
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "pl")
    fun `large Polish rating labels wrap as whole controls and remain readable`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(360.dp)) { RatingButtons({}, true) }
            }
        }
        val labels = (1..4).map { context.getString(ratingLabel(it)) }
        val boxes = labels.map { compose.onNodeWithText(it).getUnclippedBoundsInRoot() }
        boxes.forEach { org.junit.Assert.assertTrue(it.toString(), it.left >= 0.dp && it.right <= 360.dp) }
        org.junit.Assert.assertTrue(boxes.toString(), boxes[2].top > boxes[0].bottom)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "de")
    fun `heard answer fits a narrow screen with large text`() {
        val answer = com.raycoll.app.domain.session.LastAnswerResult(
            status = com.raycoll.app.domain.session.AnswerStatus.INCORRECT,
            text = "eine ziemlich lange gesprochene Antwort",
            percent = 45,
            suggestedRating = Ratings.AGAIN
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(360.dp)) { AnswerPanel(answer, false, {}) }
            }
        }
        val heard = context.getString(R.string.study_recognized_text, answer.text)
        val bounds = compose.onNodeWithText(heard, useUnmergedTree = true).getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(bounds.toString(), bounds.right <= 360.dp && bounds.left >= 0.dp)
    }
}
