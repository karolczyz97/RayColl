package com.raycoll.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

// Usuwanie przesunięciem w lewo — SwipeToDismissBox z Material 3:
// - do progu Material (SwipeToDismissBoxDefaults, 56 dp) pod spodem szare tło i kosz — puszczenie wraca kartę,
// - za progiem tło płynnie zmienia się na czerwone, telefon lekko puka; szybkie machnięcie usuwa przed progiem.

private const val HINT_DISTANCE = 0.22f
private const val HINT_DELAY_MS = 600L

/** Po tym czasie karta, która po usunięciu nadal jest na liście (zapis się nie udał), wraca na miejsce. */
private const val RESTORE_AFTER_FAILED_DELETE_MS = 4_000L

private val IconEndPadding = 24.dp

/**
 * Element listy usuwany przesunięciem w lewo. [showHint] — karta raz lekko odjeżdża i wraca
 * (podpowiedź gestu), potem [onHintShown]. [enterFromStart] — karta przywrócona po „Cofnij”
 * wjeżdża z lewej z odbiciem, potem [onEntered]. [extraActions] — dalsze akcje dla czytnika ekranu obok usuwania.
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
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit
) {
    // zwykłe remember, nie rememberSaveable: karta przywrócona po „Cofnij” nie może wrócić w stanie
    // „przesunięta do końca” (lista odtworzyłaby zapisany stan i usunęła ją drugi raz)
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val currentOnDelete by rememberUpdatedState(onDelete)
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
    val bouncy = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val hintSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var width by remember { mutableIntStateOf(0) }
    // za progiem puszczenie usuwa kartę (ten sam próg co w zachowaniu po puszczeniu palca)
    val armed by remember(state) {
        derivedStateOf { width > 0 && -state.offsetOrZero() >= threshold(width.toFloat()) }
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
            shift.animateTo(-HINT_DISTANCE, hintSpec)
            shift.animateTo(0f, bouncy)
            onHintShown()
        } else if (!enterFromStart) {
            // Nie przerywaj osobnej animacji przywrócenia po „Cofnij”.
            shift.snapTo(0f)
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
            .semantics {
                // dwa osobne wpisy customActions się nie łączą (wygrywa zewnętrzny), więc dodatkowe akcje idą tędy
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        currentOnDelete()
                        true
                    }
                ) + extraActions
            },
        backgroundContent = { DeleteBackground(armed, state, shift, shape) }
    ) {
        // podpowiedź i powrót po „Cofnij” przesuwają kartę niezależnie od gestu
        Box(Modifier.graphicsLayer { translationX = shift.value * size.width }) { content() }
    }
}

@Composable
private fun DeleteBackground(armed: Boolean, state: SwipeToDismissBoxState, shift: Animatable<Float, *>, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (armed) colors.errorContainer else colors.surfaceContainerHighest,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "delete_container"
    )
    val content by animateColorAsState(
        if (armed) colors.onErrorContainer else colors.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "delete_content"
    )
    Box(
        Modifier
            .fillMaxSize()
            // w spoczynku tło jest całkiem schowane (bez prześwitu na zaokrąglonych rogach karty)
            .graphicsLayer { alpha = if (state.offsetOrZero() != 0f || shift.value != 0f) 1f else 0f }
            .clip(shape)
            .background(container)
    ) {
        Icon(
            painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = IconEndPadding)
        )
    }
}

/** Przesunięcie karty w pikselach; 0, zanim karta zostanie zmierzona. */
private fun SwipeToDismissBoxState.offsetOrZero(): Float = runCatching { requireOffset() }.getOrDefault(0f)
