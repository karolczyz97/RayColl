package com.raycoll.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface ScreenResult {
    @Serializable data object Imported : ScreenResult

    @Serializable data class Archived(val deckId: String) : ScreenResult
}

internal const val SCREEN_RESULT = "screenResult"

internal fun decodeScreenResult(value: String?): ScreenResult? = value?.let {
    runCatching { Json.decodeFromString(ScreenResult.serializer(), it) }.getOrNull()
}

/** Żądanie ukończenia przeżywa obrót i czeka na wznowienie dokładnie tego wpisu. */
@Composable
internal fun rememberScreenCompletion(
    controller: NavHostController,
    entry: NavBackStackEntry
): (ScreenResult?) -> Unit {
    var pending by rememberSaveable(entry.id) { mutableStateOf(false) }
    var result by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry, pending) {
        if (pending) {
            completeWhenResumed(entry.lifecycle, { controller.currentBackStackEntry === entry }) {
                result?.let { controller.previousBackStackEntry?.savedStateHandle?.set(SCREEN_RESULT, it) }
                controller.popBackStack()
            }
        }
    }
    return remember(controller, entry) {
        { value ->
            if (!pending) {
                result = value?.let { Json.encodeToString(ScreenResult.serializer(), it) }
                pending = true
            }
        }
    }
}

internal suspend fun completeWhenResumed(lifecycle: Lifecycle, isCurrent: () -> Boolean, complete: () -> Unit) {
    val state = lifecycle.currentStateFlow.first {
        it == Lifecycle.State.DESTROYED || it.isAtLeast(Lifecycle.State.RESUMED)
    }
    if (state != Lifecycle.State.DESTROYED && isCurrent()) complete()
}
