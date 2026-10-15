package com.raycoll.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenCompletionTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun `completion at STARTED waits until RESUMED and runs once`() = runTest {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        var pops = 0
        val request = launch { completeWhenResumed(owner.lifecycle, { true }) { pops++ } }
        runCurrent()
        assertEquals(0, pops)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        request.join()
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(1, pops)
    }

    @Test
    fun `completion cannot pop another current entry or a destroyed entry`() = runTest {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        var pops = 0
        val request = launch { completeWhenResumed(owner.lifecycle, { false }) { pops++ } }
        runCurrent()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        request.join()
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        completeWhenResumed(owner.lifecycle, { true }) { pops++ }
        assertEquals(0, pops)
    }

    @Test
    fun `saved navigation result restores the exact archive target`() {
        val result = ScreenResult.Archived("deck-id")
        val saved = Json.encodeToString(ScreenResult.serializer(), result)
        assertEquals(result, decodeScreenResult(saved))
        org.junit.Assert.assertNull(decodeScreenResult("invalid"))
        org.junit.Assert.assertNull(decodeScreenResult(null))
    }
}
