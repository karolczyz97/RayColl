package com.raycoll.app.playback

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HolderAudioTest {
    private class Focus : AudioFocusRequester {
        var grant = true
        var held = false
        var listener: ((FocusChange) -> Unit)? = null
        override fun request(onChange: (FocusChange) -> Unit): Boolean {
            if (!grant) return false
            held = true
            listener = onChange
            return true
        }
        override fun abandon() {
            held = false
        }
    }

    private val main = StandardTestDispatcher()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val focus = Focus()
    private lateinit var holder: StudySessionHolder
    private var listened = 0
    private var visible = true

    @Before fun setup() {
        Dispatchers.setMain(main)
    }

    @After fun close() {
        if (::holder.isInitialized) holder.current()?.let(holder::release)
        Dispatchers.resetMain()
    }

    private fun start(scope: CoroutineScope, recognitionOwnsFocus: Boolean = false) {
        holder = StudySessionHolder(app, { _, _ -> }, { visible }, focus, scope)
        val deps = object : SessionDeps by FakeSessionDeps() {
            override fun acquireAudioFocus() = holder.acquireAudioFocus()
            override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
                listened++
                if (recognitionOwnsFocus) {
                    holder.withRecognitionFocus { awaitCancellation() }
                } else {
                    awaitCancellation()
                }
            }
        }
        val card = Flashcard("card", listOf("front", "back"), SrsState())
        val deck = FlashcardGroup("deck", "Deck", listOf(card))
        val engine =
            SessionEngine(
                deck,
                listOf(ListenAndCheckStep(pageIndex = 1), ShowRatingsStep()),
                deps,
                FakeClock(),
                1f,
                scope
            )
        holder.prepare(engine, deck.name)
        engine.start(deck.cards)
        holder.register(engine, deck.name, false)
    }

    @Test
    fun `microphone mode without a service pauses before listening when focus is denied`() = runTest(main) {
        focus.grant = false
        start(backgroundScope)
        runCurrent()
        assertTrue(holder.current()!!.isPaused.value)
        assertTrue(holder.focusDenied.value)
        assertEquals(0, listened)
        focus.grant = true
        assertTrue(holder.resume())
        runCurrent()
        assertFalse(holder.current()!!.isPaused.value)
        assertEquals(1, listened)
    }

    @Test
    fun `state observer leaves focus with recognition and noisy still cancels the microphone`() = runTest(main) {
        start(backgroundScope, recognitionOwnsFocus = true)
        runCurrent()
        assertEquals(1, listened)
        assertFalse(focus.held)
        assertFalse(holder.current()!!.isPaused.value)
        assertFalse(holder.focusDenied.value)
        app.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertTrue(holder.current()!!.isPaused.value)
        assertFalse(focus.held)
        assertTrue(holder.resume())
        runCurrent()
        assertEquals(2, listened)
        assertFalse(focus.held)
        assertFalse(holder.current()!!.isPaused.value)
    }

    @Test
    fun `unplugging headphones pauses without automatic resume after gain`() = runTest(main) {
        start(backgroundScope)
        runCurrent()
        assertEquals(1, listened)
        assertTrue(focus.held)
        app.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertTrue(holder.current()!!.isPaused.value)
        assertFalse(focus.held)
        focus.listener?.invoke(FocusChange.GAINED)
        assertTrue(holder.current()!!.isPaused.value)
        assertEquals(1, listened)
    }

    @Test
    fun `regaining focus in background does not restart a microphone session or hold audio`() = runTest(main) {
        start(backgroundScope)
        runCurrent()
        focus.listener!!.invoke(FocusChange.LOST_TRANSIENT)
        runCurrent()
        visible = false
        focus.listener!!.invoke(FocusChange.GAINED)
        runCurrent()
        assertTrue(holder.current()!!.isPaused.value)
        assertFalse(focus.held)
        assertEquals(1, listened)
    }

    @Test
    fun `ducking is a transient pause for speech`() {
        assertEquals(FocusChange.LOST_TRANSIENT, focusChangeOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(FocusChange.LOST_TRANSIENT, focusChangeOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusChange.LOST, focusChangeOf(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(FocusChange.GAINED, focusChangeOf(AudioManager.AUDIOFOCUS_GAIN))
    }
}
