package com.raycoll.app.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSchedulerTest {

    @Test
    fun `debounce skleja serię mutacji w jeden push`() = runTest {
        var pushes = 0
        val scheduler = SyncScheduler(this, push = { pushes++; true }, delayMs = 1200)
        repeat(5) { scheduler.enqueueDebounced() }
        advanceTimeBy(1199)
        assertEquals(0, pushes)
        advanceUntilIdle()
        assertEquals(1, pushes)
    }

    @Test
    fun `study flushuje dokładnie co 10 ocen`() = runTest {
        var pushes = 0
        val scheduler = SyncScheduler(this, push = { pushes++; true }, delayMs = 1200, flushThreshold = 10)
        repeat(9) { scheduler.enqueueStudy() }
        advanceUntilIdle()
        assertEquals(0, pushes) // 9 ocen — bez flusha, bez debounce
        scheduler.enqueueStudy() // 10-ta
        advanceUntilIdle()
        assertEquals(1, pushes)
        repeat(9) { scheduler.enqueueStudy() }
        advanceUntilIdle()
        assertEquals(1, pushes)
    }

    @Test
    fun `flush na granicy sesji domyka zaległe oceny`() = runTest {
        var pushes = 0
        val scheduler = SyncScheduler(this, push = { pushes++; true })
        repeat(2) { scheduler.enqueueStudy() } // 2 oceny poniżej progu — wiszą
        scheduler.flush()
        advanceUntilIdle()
        assertEquals(1, pushes) // granica sesji je domyka
        // po flushu nic nie wisi
        scheduler.flush()
        advanceUntilIdle()
        assertEquals(1, pushes)
    }

    @Test
    fun `immediate pomija debounce`() = runTest {
        var pushes = 0
        val scheduler = SyncScheduler(this, push = { pushes++; true })
        scheduler.enqueueImmediate()
        advanceUntilIdle()
        assertEquals(1, pushes)
    }

    @Test
    fun `nieudany push zostaje do ponowienia przy następnym flushu`() = runTest {
        var pushes = 0
        var succeed = false
        val scheduler = SyncScheduler(this, push = { pushes++; succeed })
        scheduler.enqueueImmediate()
        advanceUntilIdle()
        assertEquals(1, pushes)
        succeed = true
        scheduler.flush() // retry bez nowej mutacji
        advanceUntilIdle()
        assertEquals(2, pushes)
        scheduler.flush() // po sukcesie brak pending — no-op
        advanceUntilIdle()
        assertEquals(2, pushes)
    }

    @Test
    fun `cancel porzuca zaplanowany push`() = runTest {
        var pushes = 0
        val scheduler = SyncScheduler(this, push = { pushes++; true })
        scheduler.enqueueDebounced()
        scheduler.cancel()
        advanceUntilIdle()
        assertEquals(0, pushes)
    }

    @Test
    fun `wyjątek w push traktowany jak porażka (retry)`() = runTest {
        var attempts = 0
        val scheduler = SyncScheduler(this, push = {
            attempts++
            if (attempts == 1) throw RuntimeException("boom") else true
        })
        scheduler.enqueueImmediate()
        advanceUntilIdle()
        assertEquals(1, attempts)
        scheduler.flush()
        advanceUntilIdle()
        assertEquals(2, attempts)
    }
}
