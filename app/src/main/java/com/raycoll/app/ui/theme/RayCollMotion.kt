package com.raycoll.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Sprężyny ruchu w stylu M3 Expressive (wartości jak w `MotionScheme.expressive()`),
 * trzymane lokalnie: w Material 3 1.4.0 fabryki MotionScheme są nadal wewnętrznym API.
 * - spatial: ruch i zmiana rozmiaru — lekko sprężysty,
 * - effects: kolor i przezroczystość — bez odbicia.
 */
object RayCollMotion {
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
}
