package com.raycoll.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raycoll.app.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Przesunięcie karty w lewo odsłania przyciski akcji pod spodem; karta dojeżdża z lekkim odbiciem.

/** Akcja pod kartą; [removesItem] — karta najpierw wylatuje w lewo, dopiero potem [onClick]. */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val removesItem: Boolean = false,
    val onClick: () -> Unit
)

enum class SwipeRevealValue { Closed, Open }

/** Stan odsłonięcia akcji jednej karty. */
class SwipeRevealState {
    internal val draggable = AnchoredDraggableState(SwipeRevealValue.Closed)

    val isOpen: Boolean get() = draggable.targetValue == SwipeRevealValue.Open

    suspend fun open() = draggable.animateTo(SwipeRevealValue.Open, RevealSpring)

    suspend fun close() = draggable.animateTo(SwipeRevealValue.Closed, RevealSpring)
}

private val RevealSpring = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
private val FlyOutSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
private val ActionWidth = 84.dp
private const val ICON_MIN_SCALE = 0.5f

@Composable
fun rememberSwipeRevealState(): SwipeRevealState = remember { SwipeRevealState() }

/**
 * Karta z akcjami odsłanianymi przesunięciem w lewo. [onOpenChange] — karta odsłoniła albo schowała
 * akcje (np. żeby zamknąć inne karty listy). Akcje są też dostępne dla czytnika ekranu.
 */
@Composable
fun SwipeReveal(
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    state: SwipeRevealState = rememberSwipeRevealState(),
    onOpenChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    val revealPx = with(density) { (ActionWidth * actions.size + Spacing.s * (actions.size + 1)).toPx() }
    // wylot karty przed akcją usuwającą ją z listy, w ułamku szerokości
    val flyOut = remember { Animatable(0f) }
    val draggable = state.draggable

    LaunchedEffect(draggable) {
        snapshotFlow { draggable.settledValue }.collect { currentOnOpenChange(it == SwipeRevealValue.Open) }
    }

    fun run(action: SwipeAction) {
        scope.launch {
            if (action.removesItem) {
                flyOut.animateTo(-1f, FlyOutSpring)
                action.onClick()
            } else {
                state.close()
                action.onClick()
            }
        }
    }

    Box(
        modifier
            .onSizeChanged {
                draggable.updateAnchors(
                    DraggableAnchors {
                        SwipeRevealValue.Closed at 0f
                        SwipeRevealValue.Open at -revealPx
                    }
                )
            }
            .semantics {
                customActions = actions.map { action ->
                    CustomAccessibilityAction(action.label) {
                        action.onClick()
                        true
                    }
                }
            }
    ) {
        Row(
            Modifier
                .matchParentSize()
                .padding(Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End)
        ) {
            actions.forEach { action ->
                Surface(
                    onClick = { run(action) },
                    shape = MaterialTheme.shapes.large,
                    color = action.containerColor,
                    contentColor = action.contentColor,
                    modifier = Modifier
                        .width(ActionWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val progress = revealProgress(draggable, revealPx)
                            alpha = progress
                            val scale = ICON_MIN_SCALE + (1f - ICON_MIN_SCALE) * progress
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(action.icon, contentDescription = null)
                        Text(
                            action.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs)
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(draggable.offset.takeIf { !it.isNaN() }?.roundToInt() ?: 0, 0) }
                .graphicsLayer { translationX = flyOut.value * size.width }
                .anchoredDraggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = draggable,
                        positionalThreshold = { it / 2 },
                        animationSpec = RevealSpring
                    )
                )
        ) { content() }
    }
}

private fun revealProgress(state: AnchoredDraggableState<SwipeRevealValue>, revealPx: Float): Float {
    val offset = state.offset
    if (offset.isNaN() || revealPx <= 0f) return 0f
    return (-offset / revealPx).coerceIn(0f, 1f)
}
