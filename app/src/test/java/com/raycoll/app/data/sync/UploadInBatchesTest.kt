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

/** Znacznik aktualizacji konta pojawia się dopiero po wysłaniu wszystkich partii. */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadInBatchesTest {

    @Test
    fun `caller timeout is preserved as cancellation`() = runTest {
        try {
            withTimeout(100) { cloudOperation { awaitCancellation() } }
            fail("caller timeout must cancel")
        } catch (_: TimeoutCancellationException) {
            assertEquals(100L, testScheduler.currentTime)
        }
    }

    @Test
    fun `hung batch times out without marker and scheduler can retry`() = runTest {
        var attempts = 0
        var published = 0
        val scheduler = SyncScheduler(this, push = {
            attempts++
            uploadInBatches(listOf(1), 400, { if (attempts == 1) awaitCancellation() }, { published++ })
            true
        })
        scheduler.enqueueImmediate()
        advanceUntilIdle()
        assertEquals(CLOUD_OPERATION_TIMEOUT_MS, testScheduler.currentTime)
        assertEquals(0, published)
        scheduler.flush()
        assertEquals(2, attempts)
        assertEquals(1, published)
    }

    @Test
    fun `hung marker also times out`() = runTest {
        try {
            uploadInBatches(listOf(1), 400, {}, { awaitCancellation() })
            fail("marker must time out")
        } catch (_: IOException) {
            assertEquals(CLOUD_OPERATION_TIMEOUT_MS, testScheduler.currentTime)
        }
    }

    @Test
    fun `limit applies to each batch so a long multi batch upload completes`() = runTest {
        val batches = 5
        var published = 0
        uploadInBatches(
            writes = (1..batches * 400).toList(),
            batchSize = 400,
            commitBatch = { delay(CLOUD_OPERATION_TIMEOUT_MS - 1_000) },
            publishUpdatedAt = { published++ }
        )
        assertEquals(1, published)
        assertTrue(testScheduler.currentTime > CLOUD_OPERATION_TIMEOUT_MS * 4)
    }

    private class FakeCloud(var failOnBatch: Int? = null) {
        val committed = mutableListOf<List<Int>>()
        val documents = mutableSetOf<Int>()
        var published = 0
        var batchAttempts = 0

        suspend fun commit(batch: List<Int>) {
            batchAttempts++
            if (batchAttempts == failOnBatch) throw IOException("network lost")
            committed += batch
            documents += batch // zapis całego dokumentu: powtórka niczego nie dubluje
        }

        suspend fun publish() {
            published++
        }
    }

    private val writes = (1..950).toList()

    @Test
    fun `upload larger than one batch publishes the marker once, after the last batch`() = runTest {
        val cloud = FakeCloud()
        uploadInBatches(writes, 400, cloud::commit) {
            assertEquals("znacznik dopiero po wszystkich partiach", 3, cloud.committed.size)
            cloud.publish()
        }
        assertEquals(listOf(400, 400, 150), cloud.committed.map { it.size })
        assertEquals(1, cloud.published)
    }

    @Test
    fun `upload interrupted in the middle does not publish the marker and the retry is idempotent`() = runTest {
        val cloud = FakeCloud(failOnBatch = 2)
        try {
            uploadInBatches(writes, 400, cloud::commit, cloud::publish)
            fail("błąd partii musi przerwać wysyłkę")
        } catch (_: IOException) {
        }
        assertEquals(0, cloud.published)
        assertEquals(400, cloud.documents.size)

        // ponowienie (scheduler) wysyła wszystko od nowa — ten sam wynik, znacznik raz
        cloud.failOnBatch = null
        uploadInBatches(writes, 400, cloud::commit, cloud::publish)
        assertEquals(1, cloud.published)
        assertEquals(writes.toSet(), cloud.documents)
        assertTrue(cloud.committed.size == 4)
    }
}
