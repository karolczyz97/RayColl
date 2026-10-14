package com.raycoll.app.playback

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech

@Implements(TextToSpeech::class)
class RejectingTextToSpeech : ShadowTextToSpeech() {
    @Implementation
    override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String?): Int =
        TextToSpeech.ERROR
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [RejectingTextToSpeech::class])
class TtsRecoveryTest {
    @Test
    fun `rejected speech fails immediately and the next attempt creates an engine`() = runTest {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val player = TtsPlayer(ApplicationProvider.getApplicationContext())
        val first = async { player.speak("hello", "en-US", 1f) }
        runCurrent()
        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(engine).onInitListener.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        assertTrue(first.await() is TtsResult.Error)
        assertTrue(shadowOf(engine).isShutdown)
        assertEquals(0L, currentTime)

        val second = async { player.speak("hello", "en-US", 1f) }
        runCurrent()
        val replacement = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotSame(engine, replacement)
        second.cancel()
        runCurrent()
        assertTrue(shadowOf(replacement).isShutdown)
    }
}
