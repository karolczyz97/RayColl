package com.raycoll.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarMessagesTest {
    @Test
    fun `granting microphone permission cancels its queued message but keeps review retry`() = runTest {
        val host = SnackbarHostState()
        val messages = SnackbarMessages(host, backgroundScope)
        val retry = async { messages.showAction("Review failed", "Retry") }
        runCurrent()
        val microphone = launch { messages.showAction("Permission", "Settings", key = "microphone") }
        runCurrent()
        messages.cancelAction("microphone")
        microphone.join()
        assertEquals("Review failed", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.performAction()
        assertEquals(SnackbarResult.ActionPerformed, retry.await())
        runCurrent()
        org.junit.Assert.assertNull(host.currentSnackbarData)
    }

    @Test
    fun `three rapid messages show only the latest and do not leave stale queued messages`() = runTest {
        val host = SnackbarHostState()
        val messages = SnackbarMessages(host, backgroundScope)
        messages.showLatest("first")
        messages.showLatest("second")
        messages.showLatest("third")
        runCurrent()
        assertEquals("third", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.dismiss()
        runCurrent()
        org.junit.Assert.assertNull(host.currentSnackbarData)
    }

    @Test
    fun `latest information does not cancel retry or undo`() = runTest {
        val host = SnackbarHostState()
        val messages = SnackbarMessages(host, backgroundScope)
        val undo = async { messages.showAction("Archived", "Undo") }
        runCurrent()
        messages.showLatest("first info")
        messages.showLatest("latest info")
        runCurrent()
        assertEquals("Archived", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.performAction()
        assertEquals(SnackbarResult.ActionPerformed, undo.await())
        runCurrent()
        assertEquals("latest info", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.dismiss()
    }
}
