package com.raycoll.app.playback

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Zegarek łączy się jako aplikacja „niezaufana” — mimo to musi móc sterować nauką. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoteControlCallbackTest {
    private val controls = object : SessionControls {
        override fun play() = true
        override fun pause() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun stop() = Unit
    }
    private val player = SessionPlayer(Looper.getMainLooper(), controls)
    private val session = MediaSession.Builder(ApplicationProvider.getApplicationContext<Context>(), player).build()
    private val watch = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "com.google.android.apps.wearable.phone",
        1,
        1,
        0,
        0,
        false,
        Bundle.EMPTY,
        false
    )
    private val playbackCommands = listOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_STOP
    )

    @After
    fun teardown() {
        session.release()
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `untrusted controller gets playback controls`() {
        val result = RemoteControlCallback().onConnectAsync(session, watch).get()

        assertTrue(result.isAccepted)
        playbackCommands.forEach { assertTrue("command $it", result.availablePlayerCommands.contains(it)) }
    }

    @Test
    fun `media3 default leaves untrusted controller read-only`() {
        val result = object : MediaSession.Callback {}.onConnectAsync(session, watch).get()

        playbackCommands.forEach { assertFalse("command $it", result.availablePlayerCommands.contains(it)) }
    }
}
