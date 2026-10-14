package com.raycoll.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.raycoll.app.ui.theme.RayCollMotion

// Wspólne przejście talii między ekranami: karta i tytuł "przelatują" z listy na ekran talii.

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun deckBoundsTransform(): BoundsTransform = remember {
    val spec = RayCollMotion.spatial<Rect>()
    BoundsTransform { _, _ -> spec }
}

/** Karta talii przechodząca między ekranami. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedDeckCard(deckId: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val boundsTransform = deckBoundsTransform()
    val clip = MaterialTheme.shapes.large
    return with(sharedScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = "deck-card-$deckId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = boundsTransform,
            clipInOverlayDuringTransition = OverlayClip(clip)
        )
    }
}

/** Tytuł talii przechodzący między ekranami. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedDeckTitle(deckId: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val boundsTransform = deckBoundsTransform()
    return with(sharedScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(key = "deck-title-$deckId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = boundsTransform
        )
    }
}
