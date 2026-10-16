package com.raycoll.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.navigation3.ui.LocalNavAnimatedContentScope

// Wspólne przejście talii między ekranami: karta i tytuł "przelatują" z listy na ekran talii.
// W czasie gestu „wstecz” przelot jest wyłączony — ekran kurczy się w całości, jak w systemie.

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Konfiguracja przelotu talii z [RayCollNavHost]; bez niej (podgląd, testy ekranów) przelot jest zawsze włączony. */
internal val LocalDeckSharedContentConfig = staticCompositionLocalOf<SharedTransitionScope.SharedContentConfig?> {
    null
}

/** Przelot talii wyłączony, dopóki trwa gest „wstecz” (także jego dokończenie po puszczeniu palca). */
internal class DeckSharedContentConfig(private val isPredictiveBack: () -> Boolean) :
    SharedTransitionScope.SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
        get() = !isPredictiveBack()

    override val shouldKeepEnabledForOngoingAnimation: Boolean
        get() = false
}

@Composable
private fun deckBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return BoundsTransform { _, _ -> spec }
}

/** Karta talii przechodząca między ekranami. */
@Composable
fun Modifier.sharedDeckCard(deckId: String): Modifier {
    val clip = CardDefaults.elevatedShape
    val boundsTransform = deckBoundsTransform()
    return withSharedScopes { modifier, visibilityScope ->
        modifier.sharedBounds(
            sharedContentState = rememberDeckContentState("deck-card-$deckId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = boundsTransform,
            clipInOverlayDuringTransition = OverlayClip(clip)
        )
    }
}

/** Tytuł talii przechodzący między ekranami. */
@Composable
fun Modifier.sharedDeckTitle(deckId: String): Modifier {
    val boundsTransform = deckBoundsTransform()
    return withSharedScopes { modifier, visibilityScope ->
        modifier.sharedElement(
            sharedContentState = rememberDeckContentState("deck-title-$deckId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = boundsTransform
        )
    }
}

@Composable
private fun SharedTransitionScope.rememberDeckContentState(key: String): SharedTransitionScope.SharedContentState {
    val config = LocalDeckSharedContentConfig.current
    return if (config == null) rememberSharedContentState(key) else rememberSharedContentState(key, config)
}

/** Bez zakresów przejścia (np. ekran poza [RayCollNavHost], podgląd) modyfikator zostaje bez zmian. */
@Composable
private inline fun Modifier.withSharedScopes(
    block: @Composable SharedTransitionScope.(modifier: Modifier, visibilityScope: AnimatedVisibilityScope) -> Modifier
): Modifier {
    // zakres przejścia ekranu istnieje zawsze tam, gdzie RayCollNavHost podał zakres wspólnego przejścia
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    return sharedScope.block(this, LocalNavAnimatedContentScope.current)
}
