package com.raycoll.app.playback

import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech

/** Silnik z zainstalowanym językiem, ale bez pobranych danych głosu. */
@Implements(TextToSpeech::class)
class MissingDataTextToSpeech : ShadowTextToSpeech() {
    @Implementation
    override fun isLanguageAvailable(lang: Locale): Int = TextToSpeech.LANG_MISSING_DATA
}

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

    /** Wypowiedź z silnikiem, który zgłosił gotowość. */
    private fun TestScope.speakWithReadyEngine(text: String, language: String) = async {
        TtsPlayer(ApplicationProvider.getApplicationContext()).speak(text, language, 1f)
    }.also {
        runCurrent()
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.SUCCESS)
        runCurrent()
    }

    @Test
    fun `unsupported language reports a missing voice without speaking`() = runTest {
        val result = speakWithReadyEngine("hallo", "de-DE")
        assertEquals(TtsResult.MissingVoice("de-DE"), result.await())
        assertEquals(null, shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    @Config(shadows = [MissingDataTextToSpeech::class])
    fun `language without downloaded voice data reports a missing voice`() = runTest {
        val result = speakWithReadyEngine("hello", "en-US")
        assertEquals(TtsResult.MissingVoice("en-US"), result.await())
        assertEquals(null, shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    fun `utterance without a done callback times out with an error and drops the engine`() = runTest {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val result = speakWithReadyEngine("hello", "en-US")
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        assertEquals("hello", engine.lastSpokenText)
        // limit = 10 s + 150 ms na znak przy tempie 1
        val timeoutMs = 10_000L + 5 * 150L
        advanceTimeBy(timeoutMs - 1)
        runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(TtsResult.Error("TTS timeout"), result.await())
        assertEquals(timeoutMs, currentTime)
        assertTrue(engine.isShutdown)
    }
}
