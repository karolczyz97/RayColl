package com.raycoll.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Najnowszy zwykły komunikat zastępuje wcześniejszy; akcje Cofnij/Ponów nie giną w tej kolejce. */
class SnackbarMessages(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    private var transient: Job? = null
    private val actions = mutableMapOf<String, Job>()

    fun showLatest(message: String) {
        transient?.cancel()
        transient = scope.launch(start = CoroutineStart.UNDISPATCHED) { host.showSnackbar(message) }
    }

    suspend fun showAction(message: String, actionLabel: String, key: String? = null): SnackbarResult {
        val duration = SnackbarDuration.Long
        transient?.cancel()
        transient = null
        if (key == null) return host.showSnackbar(message, actionLabel, duration = duration)
        return coroutineScope {
            val job =
                async(start = CoroutineStart.LAZY) { host.showSnackbar(message, actionLabel, duration = duration) }
            actions.put(key, job)?.cancel()
            try {
                job.start()
                job.await()
            } finally {
                if (actions[key] === job) actions.remove(key)
            }
        }
    }

    fun cancelAction(key: String) {
        actions.remove(key)?.cancel()
    }
}

@Composable
fun rememberSnackbarMessages(host: SnackbarHostState): SnackbarMessages {
    val scope = rememberCoroutineScope()
    return remember(host, scope) { SnackbarMessages(host, scope) }
}
