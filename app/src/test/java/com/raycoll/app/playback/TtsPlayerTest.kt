package com.raycoll.app.playback

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TtsPlayerTest {

    @Test
    fun `blank text finishes at once without starting the speech engine`() = runTest {
        val player = TtsPlayer(ApplicationProvider.getApplicationContext())
        for (text in listOf("", "   ", "\n\t")) {
            assertEquals(TtsResult.Success, player.speak(text, "en-US", 1f))
        }
        // bez inicjalizacji silnika i bez limitu czasu na callback
        assertEquals(0L, currentTime)
    }
}
