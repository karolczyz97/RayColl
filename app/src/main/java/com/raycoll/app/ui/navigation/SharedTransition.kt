package com.raycoll.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.raycoll.app.ui.theme.RayCollMotion

// Wspólne przejście talii między ekranami: karta i tytuł "przelatują" z listy na ekran talii.

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

private val deckBoundsTransform = BoundsTransform { _, _ -> RayCollMotion.spatial<Rect>() }

/** Karta talii przechodząca między ekranami. */
@Composable
fun Modifier.sharedDeckCard(deckId: String): Modifier {
    val clip = MaterialTheme.shapes.large
    return withSharedScopes { modifier, visibilityScope ->
        modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "deck-card-$deckId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = deckBoundsTransform,
            clipInOverlayDuringTransition = OverlayClip(clip)
        )
    }
}

/** Tytuł talii przechodzący między ekranami. */
@Composable
fun Modifier.sharedDeckTitle(deckId: String): Modifier = withSharedScopes { modifier, visibilityScope ->
    modifier.sharedElement(
        sharedContentState = rememberSharedContentState(key = "deck-title-$deckId"),
        animatedVisibilityScope = visibilityScope,
        boundsTransform = deckBoundsTransform
    )
}

/** Bez zakresów przejścia (np. ekran poza NavHostem, podgląd) modyfikator zostaje bez zmian. */
@Composable
private inline fun Modifier.withSharedScopes(
    block: @Composable SharedTransitionScope.(modifier: Modifier, visibilityScope: AnimatedVisibilityScope) -> Modifier
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return sharedScope.block(this, visibilityScope)
}
