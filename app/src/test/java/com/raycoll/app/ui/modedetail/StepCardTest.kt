package com.raycoll.app.ui.modedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.RevealStyle
import org.junit.Assert.assertEquals
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
}
