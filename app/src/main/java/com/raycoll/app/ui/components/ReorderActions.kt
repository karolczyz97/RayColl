package com.raycoll.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/** Akcje „wyżej” i „niżej” dla elementu z miejsca [index] na liście długości [count]; skrajne mają tylko jedną. */
fun moveActions(
    index: Int,
    count: Int,
    upLabel: String,
    downLabel: String,
    onMove: (from: Int, to: Int) -> Unit
): List<CustomAccessibilityAction> = listOfNotNull(
    CustomAccessibilityAction(upLabel) {
        onMove(index, index - 1)
        true
    }.takeIf { index > 0 },
    CustomAccessibilityAction(downLabel) {
        onMove(index, index + 1)
        true
    }.takeIf { index < count - 1 }
)

/** Czytnik ekranu nie przytrzyma ani nie przeciągnie, więc element dostaje akcje przesunięcia o jedno miejsce. */
fun Modifier.reorderActions(actions: List<CustomAccessibilityAction>): Modifier = semantics { customActions = actions }
