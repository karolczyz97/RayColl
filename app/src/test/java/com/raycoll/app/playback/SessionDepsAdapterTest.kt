package com.raycoll.app.playback

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.session.ListenStatus
import com.raycoll.app.domain.session.SpeakResult
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** Spięcie silnika sesji z zapisem ocen oraz z prawdziwym TTS/STT (Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionDepsAdapterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repos = VmTestHarness.create(app, FakeClock())
    private val holder = StudySessionHolder(
        app,
        { _, _ -> },
        { true },
        object : AudioFocusRequester {
            override fun request(onChange: (FocusChange) -> Unit) = true
            override fun abandon() = Unit
        }
    )

    private fun adapter(scope: CoroutineScope) = SessionDepsAdapter(
        TtsPlayer(app),
        SttRecognizer(app),
        mock(HapticAndSoundPlayer::class.java),
        repos.deckRepository,
        scope,
        holder
    )

    @Test
    fun `review is saved at once and only once`() = runTest {
        val deckId = repos.deckRepository.addDeckWithCards(
            "Deck",
            listOf("en", "pl"),
            listOf("A", "B"),
            listOf(listOf("q", "a"))
        )
        val cardId = repos.deckRepository.getDeck(deckId)!!.cards.single().id
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler))

        adapter(appScope).onCardReviewed(deckId, cardId, 3, null)

        // bez przepompowania dispatchera: zapis ruszył od razu w wywołaniu (UNDISPATCHED)
        assertEquals(1, repos.deckRepository.getDeck(deckId)!!.cards.single().srsState.repetitions)
        advanceUntilIdle()
        assertEquals(1, repos.deckRepository.getDeck(deckId)!!.cards.single().srsState.repetitions)
        assertEquals(listOf(1), repos.snapshot().activityHeatmap.values.toList())
    }

    @Test
    fun `listening waits until the current speech has finished`() = runTest {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val deps = adapter(backgroundScope)
        val speech = async { deps.speak("hello", "en-US", 1f) }
        runCurrent()
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.SUCCESS)
        runCurrent()

        val listening = async { deps.listen("en-US", 5_000) {} }
        runCurrent()
        // bez zgody na mikrofon nasłuch kończy się od razu — o ile dostanie turę
        assertFalse(listening.isCompleted)

        // silnik nie zgłosił końca wypowiedzi: tura zwalnia się dopiero po limicie czasu mowy
        advanceUntilIdle()
        assertEquals(SpeakResult.ERROR, speech.await())
        assertEquals(ListenStatus.ERROR, listening.await().status)
    }
}
