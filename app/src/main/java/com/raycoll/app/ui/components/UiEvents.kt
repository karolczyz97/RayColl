package com.raycoll.app.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Jednorazowe zdarzenia z ViewModelu do ekranu (komunikat, powrót). Zdarzenie
 * wysłane, gdy ekran akurat nie słucha (np. w trakcie obrotu), czeka i nie ginie.
 */
class UiEvents<E> {
    private val channel = Channel<E>(Channel.BUFFERED)
    val flow: Flow<E> = channel.receiveAsFlow()

    fun send(event: E) {
        channel.trySend(event)
    }
}

/**
 * Zapis z ekranu (baza, ustawienia): błąd zapisu (np. brak miejsca) nie wywraca aplikacji,
 * tylko wywołuje [onFailure] — ekran pokazuje „Nie udało się zapisać zmian”. Anulowanie
 * (zamknięcie ekranu) przechodzi dalej bez komunikatu.
 */
fun CoroutineScope.launchSaving(onFailure: () -> Unit, block: suspend () -> Unit): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("launchSaving", "Saving failed", e)
        onFailure()
    }
}

/** Odbiera zdarzenia, gdy ekran jest widoczny. */
@Composable
fun <E> CollectEvents(events: Flow<E>, onEvent: suspend (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler = rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { handler.value(it) }
        }
    }
}
