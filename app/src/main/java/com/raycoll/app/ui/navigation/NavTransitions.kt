package com.raycoll.app.ui.navigation

import android.view.RoundedCorner
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent

// Przejścia między ekranami jak w systemie Android:
// - otwarcie: nowy ekran wsuwa się z prawej o 1/10 szerokości i szybko pojawia, poprzedni odsuwa się w lewo,
// - powrót przyciskiem: odwrotnie,
// - gest „wstecz”: ekran pod palcem kurczy się do 90 % i przesuwa lekko za palcem, poprzedni czeka pod spodem;
//   po puszczeniu ekran odjeżdża i znika, po cofnięciu palca wraca na miejsce.
// W trakcie każdego przejścia oba ekrany mają zaokrąglone rogi jak ekran telefonu.

private const val DURATION_MS = 450
private const val FADE_MS = 100
private const val FADE_DELAY_MS = 35
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// gest: postęp palca (0..1) przewija przejście; do GESTURE_END ekran tylko się kurczy, dalej odjeżdża i znika
private const val PREDICTIVE_MS = 400
private const val GESTURE_END = 0.5f
private const val GESTURE_SCALE = 0.9f
private const val COMMIT_SCALE = 0.85f
private val GestureShift = 24.dp
private val CommitShift = 96.dp
private val ParallaxShift = 48.dp

private val CornerFallback = 28.dp
private const val CORNER_MS = 100
private const val CORNER_GESTURE_SPEED = 4f

private fun parallax(width: Int) = width / 10

internal fun forwardTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(DURATION_MS, easing = Emphasized)) { parallax(it) } +
        fadeIn(tween(FADE_MS, delayMillis = FADE_DELAY_MS, easing = LinearEasing)),
    initialContentExit = slideOutHorizontally(tween(DURATION_MS, easing = Emphasized)) { -parallax(it) }
)

internal fun popTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(DURATION_MS, easing = Emphasized)) { -parallax(it) },
    initialContentExit = slideOutHorizontally(tween(DURATION_MS, easing = Emphasized)) { parallax(it) } +
        fadeOut(tween(FADE_MS, delayMillis = FADE_DELAY_MS, easing = LinearEasing))
)

/** Przejście przewijane gestem „wstecz” (od [swipeEdge]); po puszczeniu dogrywa się reszta osi czasu. */
internal fun predictivePopTransition(density: Density, swipeEdge: Int): ContentTransform {
    // gest od lewej krawędzi przesuwa ekran w prawo, od prawej w lewo
    val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
    val gestureEnd = (PREDICTIVE_MS * GESTURE_END).toInt()
    val gesturePx = with(density) { GestureShift.roundToPx() } * direction
    val commitPx = with(density) { CommitShift.roundToPx() } * direction
    val parallaxPx = with(density) { ParallaxShift.roundToPx() }
    return ContentTransform(
        targetContentEnter = slideInHorizontally(tween(PREDICTIVE_MS, easing = LinearEasing)) { -parallaxPx },
        initialContentExit = scaleOut(
            keyframes {
                durationMillis = PREDICTIVE_MS
                1f at 0 using LinearEasing
                GESTURE_SCALE at gestureEnd using LinearEasing
            },
            targetScale = COMMIT_SCALE
        ) + slideOut(
            keyframes {
                durationMillis = PREDICTIVE_MS
                IntOffset.Zero at 0 using LinearEasing
                IntOffset(gesturePx, 0) at gestureEnd using LinearEasing
            }
        ) { IntOffset(commitPx, 0) } + fadeOut(
            keyframes {
                durationMillis = PREDICTIVE_MS
                1f at gestureEnd using LinearEasing
            }
        )
    )
}

/**
 * Wygląd ekranu pod palcem w trakcie gestu „wstecz” ([progress] 0..1) — ten sam co przy zamykaniu
 * ekranu gestem; dla ekranów, które zamiast zamknięcia pytają o potwierdzenie.
 */
internal fun GraphicsLayerScope.predictiveBackGesture(progress: Float, fromRightEdge: Boolean, cornerRadius: Dp) {
    val gesture = (progress / GESTURE_END).coerceIn(0f, 1f)
    val scale = 1f - (1f - GESTURE_SCALE) * gesture
    scaleX = scale
    scaleY = scale
    translationX = GestureShift.toPx() * gesture * if (fromRightEdge) -1f else 1f
    clip = gesture > 0f
    // rogi zaokrąglają się na samym początku gestu, potem zostają
    shape = RoundedCornerShape(cornerRadius.toPx() * (gesture * CORNER_GESTURE_SPEED).coerceAtMost(1f))
}

/**
 * Zaokrąglone rogi ekranu w trakcie przejścia (promień jak rogi wyświetlacza); po zakończeniu
 * przejścia ekran wraca do prostokąta bez przycinania.
 */
@Composable
internal fun rememberTransitionCornersDecorator(): NavEntryDecorator<NavKey> {
    val radius = screenCornerRadius()
    return remember(radius) {
        NavEntryDecorator(decorate = { entry ->
            Box(Modifier.transitionCorners(radius)) { entry.Content() }
        })
    }
}

@Composable
private fun Modifier.transitionCorners(radius: Dp): Modifier {
    val transition = LocalNavAnimatedContentScope.current.transition
    val corner by transition.animateDp(transitionSpec = { tween(CORNER_MS) }, label = "screen_corners") { state ->
        if (state == EnterExitState.Visible) 0.dp else radius
    }
    return graphicsLayer {
        val px = corner.toPx()
        clip = px > 0f
        shape = RoundedCornerShape(px)
    }
}

/** Promień rogów wyświetlacza (gdy system go nie podaje — typowe 28 dp). */
@Composable
internal fun screenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            ?.takeIf { it > 0 }
            ?.let { with(density) { it.toDp() } }
            ?: CornerFallback
    }
}
