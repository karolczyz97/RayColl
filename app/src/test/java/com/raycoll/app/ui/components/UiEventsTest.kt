package com.raycoll.app.ui.components

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Zdarzenia z ViewModelu: zawieszony handler (np. otwarty komunikat) nie wstrzymuje kolejnych. */
@RunWith(RobolectricTestRunner::class)
class UiEventsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `suspended handler does not delay later events`() {
        val events = UiEvents<Int>()
        val gate = CompletableDeferred<Unit>()
        val handled = mutableStateListOf<Int>()
        compose.setContent {
            CollectEvents(events.flow) { event ->
                if (event == 1) gate.await()
                handled += event
            }
        }
        events.send(1)
        events.send(2)
        compose.waitUntil(5_000) { handled.contains(2) }
        assertEquals(listOf(2), handled.toList())

        gate.complete(Unit)
        compose.waitUntil(5_000) { handled.size == 2 }
        assertEquals(listOf(2, 1), handled.toList())
    }

    @Test
    fun `non suspending handlers keep the order of events`() {
        val events = UiEvents<Int>()
        val handled = mutableStateListOf<Int>()
        compose.setContent { CollectEvents(events.flow) { event -> handled += event } }
        (1..5).forEach(events::send)
        compose.waitUntil(5_000) { handled.size == 5 }
        assertEquals(listOf(1, 2, 3, 4, 5), handled.toList())
    }
}
