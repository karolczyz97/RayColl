package com.raycoll.app.testing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput

/** Węzeł z akcją dla czytnika ekranu o danej nazwie (np. „Move down”). */
fun hasCustomAction(label: String) = SemanticsMatcher("ma akcję „$label”") { node ->
    SemanticsActions.CustomActions in node.config &&
        node.config[SemanticsActions.CustomActions].any { it.label == label }
}

fun SemanticsNodeInteraction.customActionLabels(): List<String> =
    fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

fun ComposeTestRule.performCustomAction(node: SemanticsNodeInteraction, label: String) {
    val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
    runOnUiThread { action.action() }
    waitForIdle()
}

/**
 * Przytrzymanie [node], przeciągnięcie w dół o [steps] × [stepPx] px i puszczenie. [whileHeld] sprawdza stan,
 * zanim palec się oderwie; dalsze ruchy idą w okno, bo przeciągany element zmienia miejsce (i opis).
 */
fun ComposeTestRule.longPressAndDrag(
    node: SemanticsNodeInteraction,
    steps: Int = 12,
    stepPx: Float = 20f,
    whileHeld: () -> Unit = {}
) {
    node.performTouchInput { down(center) }
    mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
    onRoot().performTouchInput { repeat(steps) { moveBy(Offset(0f, stepPx)) } }
    whileHeld()
    onRoot().performTouchInput { up() }
    waitForIdle()
}

private const val LONG_PRESS_WAIT_MS = 1_000L
