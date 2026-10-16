package com.raycoll.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.painter.Painter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Przesunięcie karty w lewo odsłania przyciski akcji pod spodem; karta dojeżdża z lekkim odbiciem.

/** Akcja pod kartą; [removesItem] — karta najpierw wylatuje w lewo, dopiero potem [onClick]. */
class SwipeAction(
    val label: String,
    val icon: Painter,
    val containerColor: Color,
    val contentColor: Color,
    val removesItem: Boolean = false,
    val onClick: () -> Unit
)

enum class SwipeRevealValue { Closed, Open }

/** Stan odsłonięcia akcji jednej karty. */
class SwipeRevealState(private val animationSpec: AnimationSpec<Float>) {
    internal val draggable = AnchoredDraggableState(SwipeRevealValue.Closed)

    val isOpen: Boolean get() = draggable.targetValue == SwipeRevealValue.Open

    suspend fun open() = draggable.animateTo(SwipeRevealValue.Open, animationSpec)

    suspend fun close() = draggable.animateTo(SwipeRevealValue.Closed, animationSpec)
}

private val ActionWidth = 84.dp
private const val ICON_MIN_SCALE = 0.5f
private const val HINT_DELAY_MS = 600L
private const val HINT_FRACTION = 0.5f

@Composable
fun rememberSwipeRevealState(): SwipeRevealState {
    val animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return remember(animationSpec) { SwipeRevealState(animationSpec) }
}

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
    showHint: Boolean = false,
    onHintShown: () -> Unit = {},
    resetKey: Int = 0,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    val revealPx = with(density) { (ActionWidth * actions.size + Spacing.s * (actions.size + 1)).toPx() }
    // wylot karty przed akcją usuwającą ją z listy, w ułamku szerokości
    val flyOut = remember(resetKey) { Animatable(0f) }
    val draggable = state.draggable
    // podpowiedź gestu: karta raz lekko odjeżdża, uchyla akcje i wraca (w pikselach, obok przesunięcia palcem)
    val peek = remember { Animatable(0f) }
    val hintSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(resetKey) {
        if (resetKey != 0) state.close()
    }
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(HINT_DELAY_MS)
            peek.animateTo(-revealPx * HINT_FRACTION, hintSpec)
            peek.animateTo(0f, hintSpec)
            onHintShown()
        }
    }

    LaunchedEffect(draggable) {
        snapshotFlow { draggable.settledValue }.collect { currentOnOpenChange(it == SwipeRevealValue.Open) }
    }

    fun run(action: SwipeAction) {
        scope.launch {
            if (action.removesItem) {
                flyOut.animateTo(-1f, hintSpec)
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
                    shape = CardDefaults.elevatedShape,
                    color = action.containerColor,
                    contentColor = action.contentColor,
                    modifier = Modifier
                        .width(ActionWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val progress = revealProgress(draggable, peek.value, revealPx)
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
                .offset { IntOffset(((draggable.offset.takeIf { !it.isNaN() } ?: 0f) + peek.value).roundToInt(), 0) }
                .graphicsLayer { translationX = flyOut.value * size.width }
                .anchoredDraggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = draggable,
                        positionalThreshold = { it / 2 },
                        animationSpec = hintSpec
                    )
                )
        ) { content() }
    }
}

private fun revealProgress(state: AnchoredDraggableState<SwipeRevealValue>, peek: Float, revealPx: Float): Float {
    val offset = (state.offset.takeIf { !it.isNaN() } ?: 0f) + peek
    if (revealPx <= 0f) return 0f
    return (-offset / revealPx).coerceIn(0f, 1f)
}
