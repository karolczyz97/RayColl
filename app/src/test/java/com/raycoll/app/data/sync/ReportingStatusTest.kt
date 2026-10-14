package com.raycoll.app.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Limit czasu ma każde żądanie do chmury, nie cała synchronizacja (A15). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportingStatusTest {

    private val status = MutableStateFlow(SyncStatus.IDLE)

    @Test
    fun `sync made of many requests may take longer than one request limit`() = runTest {
        val result = reportingStatus(status, onError = false) {
            repeat(4) { cloudOperation { delay(CLOUD_OPERATION_TIMEOUT_MS - 1_000) } }
            true
        }
        assertTrue(result)
        assertEquals(SyncStatus.IDLE, status.value)
        assertTrue(testScheduler.currentTime > CLOUD_OPERATION_TIMEOUT_MS * 3)
    }

    @Test
    fun `hung request ends the sync with an error after its limit`() = runTest {
        val result = reportingStatus(status, onError = false) {
            cloudOperation { awaitCancellation() }
        }
        assertEquals(false, result)
        assertEquals(SyncStatus.ERROR, status.value)
        assertEquals(CLOUD_OPERATION_TIMEOUT_MS, testScheduler.currentTime)
    }
}
