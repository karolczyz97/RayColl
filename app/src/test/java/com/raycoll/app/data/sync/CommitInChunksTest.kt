package com.raycoll.app.data.sync

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommitInChunksTest {
    @Test fun `caller timeout remains cancellation`() = runTest {
        try {
            withTimeout(100) { commitInChunks(listOf(1), 400) { awaitCancellation() } }
            fail("caller cancellation must propagate")
        } catch (_: TimeoutCancellationException) {
            assertEquals(100L, testScheduler.currentTime)
        }
    }

    @Test fun `hung transaction times out and scheduler can retry`() = runTest {
        var attempts = 0
        var committed = 0
        val scheduler = SyncScheduler(this, push = {
            attempts++
            commitInChunks(listOf(1), 400) {
                if (attempts == 1) awaitCancellation()
                committed++
            }
            true
        })
        scheduler.enqueueImmediate()
        advanceUntilIdle()
        assertEquals(CLOUD_OPERATION_TIMEOUT_MS, testScheduler.currentTime)
        assertEquals(0, committed)
        assertTrue(scheduler.flush())
        assertEquals(1, committed)
    }

    @Test fun `each transaction has its own timeout and no writes are dropped`() = runTest {
        val sizes = mutableListOf<Int>()
        commitInChunks((1..1001).toList(), 400) {
            delay(CLOUD_OPERATION_TIMEOUT_MS - 1000)
            sizes += it.size
        }
        assertEquals(listOf(400, 400, 201), sizes)
        assertTrue(testScheduler.currentTime > CLOUD_OPERATION_TIMEOUT_MS * 2)
    }

    @Test fun `a conflict stops following transactions`() = runTest {
        var commits = 0
        try {
            commitInChunks((1..1001).toList(), 400) {
                if (commits == 1) throw CloudChangedException()
                commits++
            }
            fail("conflict must stop the upload")
        } catch (_: CloudChangedException) {
            assertEquals(1, commits)
        }
    }

    @Test fun `generation only transaction is still bounded`() = runTest {
        try {
            commitInChunks(emptyList<Int>(), 400) { awaitCancellation() }
            fail("generation publication must time out")
        } catch (_: IOException) {
            assertEquals(CLOUD_OPERATION_TIMEOUT_MS, testScheduler.currentTime)
        }
    }
}
