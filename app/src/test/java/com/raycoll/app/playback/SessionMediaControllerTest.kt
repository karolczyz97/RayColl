package com.raycoll.app.playback

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Prawdziwy MediaController (tak łączą się powiadomienie, ekran blokady, Android Auto
 * i słuchawki przez sesję): każda zadeklarowana komenda nawigacji zmienia kartę dokładnie raz.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionMediaControllerTest {
    private val commands = mutableListOf<String>()
    private val controls = object : SessionControls {
        override fun play(): Boolean {
            commands += "play"
            return true
        }
        override fun pause() {
            commands += "pause"
        }
        override fun next() {
            commands += "next"
        }
        override fun previous() {
            commands += "previous"
        }
        override fun stop() {
            commands += "stop"
        }
    }
    private lateinit var player: SessionPlayer
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        player = SessionPlayer(Looper.getMainLooper(), controls)
        session = MediaSession.Builder(context, player.withCardNavigation(controls)).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        idle()
        controller = future.get(5, TimeUnit.SECONDS)
        idle()
    }

    @After
    fun teardown() {
        controller.release()
        session.release()
        player.release()
        idle()
    }

    @Test
    fun `controller sees every navigation command the session declares`() {
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_STOP
        ).forEach { assertTrue("komenda $it", controller.isCommandAvailable(it)) }
    }

    @Test
    fun `each navigation command changes the card exactly once`() {
        controller.seekToNext()
        idle()
        assertEquals(listOf("next"), commands)
        controller.seekToNextMediaItem()
        idle()
        controller.seekToPrevious()
        idle()
        controller.seekToPreviousMediaItem()
        idle()
        assertEquals(listOf("next", "next", "previous", "previous"), commands)
    }

    @Test
    fun `play and pause from the controller reach the session once each`() {
        controller.play()
        idle()
        assertTrue(controller.playWhenReady)
        controller.pause()
        idle()
        assertFalse(controller.playWhenReady)
        assertEquals(listOf("play", "pause"), commands)
    }
}
