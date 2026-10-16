package com.raycoll.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Wspólne ruchy z MaterialTheme.motionScheme (Expressive), żeby każda lista i każdy wysuwany element
// poruszał się tak samo — bez ręcznie dobranych czasów.

/** Wstawianie, usuwanie i przestawianie elementu listy. */
@Composable
fun Modifier.animateListItem(scope: LazyItemScope): Modifier = with(scope) {
    this@animateListItem.animateItem(
        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()
    )
}

/** Pojawienie się elementu, który rozsuwa treść pod sobą (ramki z uwagami, paski postępu). */
@Composable
fun expandIn(): EnterTransition = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())

/** Zniknięcie elementu, po którym treść pod nim się zsuwa. */
@Composable
fun shrinkOut(): ExitTransition = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
