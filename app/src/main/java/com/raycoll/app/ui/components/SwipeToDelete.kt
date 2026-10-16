package com.raycoll.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// Usuwanie przesunięciem w lewo, jak w Gmailu:
// - do progu ([DELETE_THRESHOLD] szerokości) pod spodem szare tło i szary kosz — puszczenie wraca kartę,
// - za progiem od kosza rozlewa się czerwone koło, kosz podskakuje, telefon lekko puka,
// - szybkie machnięcie usuwa nawet przed progiem,
// - karta w trakcie przesuwania unosi się jak kartka (przechył, cień) i wylatuje obrócona.

private const val DELETE_THRESHOLD = 0.4f
private const val MAX_TILT_DEGREES = 6f
private const val LIFTED_SCALE = 0.97f
private const val FULL_LIFT_AT = 0.25f
private const val HINT_DISTANCE = 0.22f
private const val HINT_DELAY_MS = 600L
private const val ICON_POP_SCALE = 1.3f

/** Po tym czasie karta, która po usunięciu nadal jest na liście (zapis się nie udał), wraca na miejsce. */
private const val RESTORE_AFTER_FAILED_DELETE_MS = 4_000L

private val LiftElevation = 8.dp
private val IconEndPadding = 24.dp
private val IconHalfSize = 12.dp

/**
 * Element listy usuwany przesunięciem w lewo. [showHint] — karta raz lekko odjeżdża i wraca
 * (podpowiedź gestu), potem [onHintShown]. [enterFromStart] — karta przywrócona po „Cofnij”
 * wjeżdża z lewej z odbiciem, potem [onEntered].
 */
@Composable
fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    deleteLabel: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    showHint: Boolean = false,
    onHintShown: () -> Unit = {},
    enterFromStart: Boolean = false,
    onEntered: () -> Unit = {},
    content: @Composable () -> Unit
) {
    // zwykłe remember, nie rememberSaveable: karta przywrócona po „Cofnij” nie może wrócić w stanie
    // „przesunięta do końca” (lista odtworzyłaby zapisany stan i usunęła ją drugi raz)
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { it * DELETE_THRESHOLD } }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    // jedno usunięcie na jedno przesunięcie, także gdy karta jeszcze znika z listy
    var deleted by remember { mutableStateOf(false) }
    val dismiss = remember {
        { _: SwipeToDismissBoxValue ->
            if (!deleted) {
                deleted = true
                currentOnDelete()
            }
        }
    }
    val haptics = LocalHapticFeedback.current
    // przesunięcie karty niezależne od gestu (podpowiedź, powrót po „Cofnij”) — w ułamku szerokości
    val shift = remember { Animatable(if (enterFromStart) -1f else 0f) }
    val bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    var width by remember { mutableIntStateOf(0) }
    // za progiem puszczenie usuwa kartę (ten sam próg co w zachowaniu po puszczeniu palca)
    val armed by remember(state) {
        derivedStateOf { width > 0 && -state.offsetOrZero() >= width * DELETE_THRESHOLD }
    }

    LaunchedEffect(state) {
        snapshotFlow { armed }
            .distinctUntilChanged()
            .drop(1)
            .collect { armed ->
                haptics.performHapticFeedback(
                    if (armed) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick
                )
            }
    }
    LaunchedEffect(enterFromStart) {
        if (enterFromStart) {
            if (state.currentValue != SwipeToDismissBoxValue.Settled) state.snapTo(SwipeToDismissBoxValue.Settled)
            shift.animateTo(0f, bouncy)
            onEntered()
        }
    }
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(HINT_DELAY_MS)
            shift.animateTo(-HINT_DISTANCE, spring(dampingRatio = Spring.DampingRatioNoBouncy))
            shift.animateTo(0f, bouncy)
            onHintShown()
        }
    }
    LaunchedEffect(state.settledValue) {
        if (state.settledValue == SwipeToDismissBoxValue.EndToStart) {
            delay(RESTORE_AFTER_FAILED_DELETE_MS)
            state.reset()
            deleted = false
        }
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = dismiss,
        modifier = modifier
            .onSizeChanged { width = it.width }
            // puszczenie palca za progiem usuwa kartę także bez machnięcia (sama biblioteka wybrałaby
            // wtedy najbliższą pozycję, czyli próg wypadałby w połowie szerokości)
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    if (armed) scope.launch { state.dismiss(SwipeToDismissBoxValue.EndToStart) }
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        currentOnDelete()
                        true
                    }
                )
            },
        backgroundContent = { DeleteBackground(armed, state, shift, shape) }
    ) {
        Box(
            Modifier.graphicsLayer {
                val width = size.width
                if (width <= 0f) return@graphicsLayer
                translationX = shift.value * width
                val fraction = ((state.offsetOrZero() + translationX) / width).coerceIn(-1f, 1f)
                val lift = (abs(fraction) / FULL_LIFT_AT).coerceAtMost(1f)
                rotationZ = fraction * MAX_TILT_DEGREES
                val scale = 1f - (1f - LIFTED_SCALE) * lift
                scaleX = scale
                scaleY = scale
                shadowElevation = LiftElevation.toPx() * lift
                this.shape = shape
            }
        ) { content() }
    }
}

@Composable
private fun DeleteBackground(armed: Boolean, state: SwipeToDismissBoxState, shift: Animatable<Float, *>, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    val reveal by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "delete_reveal"
    )
    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(armed) {
        if (armed) {
            iconScale.animateTo(ICON_POP_SCALE, spring(stiffness = Spring.StiffnessHigh))
            iconScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            // w spoczynku tło jest całkiem schowane (bez prześwitu na zaokrąglonych rogach karty)
            .graphicsLayer { alpha = if (state.offsetOrZero() != 0f || shift.value != 0f) 1f else 0f }
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .drawBehind {
                val center = Offset(size.width - (IconEndPadding + IconHalfSize).toPx(), size.height / 2)
                drawCircle(colors.error, radius = reveal * hypot(size.width, size.height), center = center)
            }
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = lerp(colors.onSurfaceVariant, colors.onError, reveal),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = IconEndPadding)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                }
        )
    }
}

/** Przesunięcie karty w pikselach; 0, zanim karta zostanie zmierzona. */
private fun SwipeToDismissBoxState.offsetOrZero(): Float = runCatching { requireOffset() }.getOrDefault(0f)
