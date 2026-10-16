package com.raycoll.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface ScreenResult {
    @Serializable data object Imported : ScreenResult

    @Serializable data class Archived(val deckId: String) : ScreenResult
}

internal fun encodeScreenResult(value: ScreenResult?): String? =
    value?.let { Json.encodeToString(ScreenResult.serializer(), it) }

internal fun decodeScreenResult(value: String?): ScreenResult? = value?.let {
    runCatching { Json.decodeFromString(ScreenResult.serializer(), it) }.getOrNull()
}

/**
 * Żądanie ukończenia ekranu przeżywa obrót i czeka, aż ekran będzie wznowiony i nadal na wierzchu
 * ([isCurrent]); dopiero wtedy [complete] (zapis wyniku i zamknięcie ekranu).
 */
@Composable
internal fun rememberScreenCompletion(isCurrent: () -> Boolean, complete: (String?) -> Unit): (ScreenResult?) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentIsCurrent by rememberUpdatedState(isCurrent)
    val currentComplete by rememberUpdatedState(complete)
    var pending by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(lifecycle, pending) {
        if (pending) completeWhenResumed(lifecycle, { currentIsCurrent() }) { currentComplete(result) }
    }
    return remember {
        { value ->
            if (!pending) {
                result = encodeScreenResult(value)
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
