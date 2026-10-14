package com.raycoll.app.ui.modedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.StepCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StepCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `flip reveal step offers both reveal styles and keeps the rest of the step`() {
        var step: ModeStep by mutableStateOf(CompoundStep(version = 3, params = CompoundParams.FlipReveal()))
        compose.setContent {
            StepCard(
                number = 1,
                step = step,
                canMoveUp = false,
                canMoveDown = false,
                onMoveUp = {},
                onMoveDown = {},
                onDelete = {},
                onUpdate = { step = it }
            )
        }

        compose.onNodeWithText("All at once").assertIsSelected()
        compose.onNodeWithText("One at a time").performClick()
        assertEquals(CompoundStep(version = 3, params = CompoundParams.FlipReveal(RevealStyle.NEXT)), step)
        compose.onNodeWithText("One at a time").assertIsSelected()

        compose.onNodeWithText("All at once").performClick()
        assertEquals(CompoundStep(version = 3, params = CompoundParams.FlipReveal(RevealStyle.ALL)), step)
    }

    @Test
    fun `condition chips wrap to the next line instead of running off the card at a large font`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
                Box(Modifier.width(320.dp)) { TestCard(1, ShowPageStep()) }
            }
        }

        listOf("Always", "If correct", "If wrong").forEach { label ->
            val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue("$label sticks out of the card: $bounds", bounds.right <= 320.dp)
        }
    }

    @Test
    fun `tapping the label of a switch toggles it`() {
        var step: ModeStep by mutableStateOf(CompoundStep(params = CompoundParams.PresentFront(speak = true)))
        compose.setContent { TestCard(1, step) { step = it } }

        compose.onNodeWithText("Speak").assertIsToggleable().performClick()
        assertEquals(CompoundParams.PresentFront(speak = false), (step as CompoundStep).params)
    }

    @Test
    fun `buttons of different cards have different descriptions`() {
        compose.setContent {
            Column {
                TestCard(1, ShowPageStep(pageIndex = 0))
                TestCard(2, ShowPageStep(pageIndex = 1))
            }
        }

        compose.onNodeWithContentDescription("Delete: 1. Show page 1").assertExists()
        compose.onNodeWithContentDescription("Delete: 2. Show page 2").assertExists()
    }

    @Test
    fun `a rating shows its name under the field`() {
        var step: ModeStep by mutableStateOf(AutoRateFixedStep(rating = 3))
        compose.setContent { TestCard(1, step) { step = it } }

        compose.onNodeWithText("Good").assertExists()
    }

    @Test
    fun `condition of a step is selectable`() {
        var step: ModeStep by mutableStateOf(ShowPageStep())
        compose.setContent { TestCard(1, step) { step = it } }

        compose.onNodeWithText("If wrong").performClick()
        assertEquals(StepCondition.WRONG, step.condition)
        compose.onNodeWithText("If wrong").assertIsSelected()
    }
}

@Composable
private fun TestCard(number: Int, step: ModeStep, onUpdate: (ModeStep) -> Unit = {}) {
    StepCard(
        number = number,
        step = step,
        canMoveUp = false,
        canMoveDown = false,
        onMoveUp = {},
        onMoveDown = {},
        onDelete = {},
        onUpdate = onUpdate
    )
}
