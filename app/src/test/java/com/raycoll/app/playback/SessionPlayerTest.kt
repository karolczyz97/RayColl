package com.raycoll.app.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionPlayerTest {
    private val commands = mutableListOf<String>()
    private val controls = object : SessionControls {
        var allowPlay = true
        override fun play(): Boolean {
            commands += "play"
            return allowPlay
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

    @Before
    fun setup() {
        player = SessionPlayer(Looper.getMainLooper(), controls)
    }

    @After
    fun teardown() {
        player.release()
    }

    @Test
    fun `metadata contains card title deck name and progress`() {
        player.update(SessionPlayback("Card", "Deck", "2/5", true))
        assertEquals("Card", player.mediaMetadata.title.toString())
        assertEquals("Deck", player.mediaMetadata.artist.toString())
        assertEquals("2/5", player.mediaMetadata.subtitle.toString())
        assertTrue(player.isPlaying)
    }

    @Test
    fun `notification controllers can access the session timeline`() {
        assertTrue(player.isCommandAvailable(Player.COMMAND_GET_TIMELINE))
        assertFalse(player.currentTimeline.isEmpty)
        assertEquals(1, player.mediaItemCount)
    }

    @Test
    fun `play and pause are routed to the session and update playing state`() {
        player.play()
        assertTrue(player.isPlaying)
        player.pause()
        assertFalse(player.isPlaying)
        assertEquals(listOf("play", "pause"), commands)
    }

    @Test
    fun `card navigation works despite the single-item playlist`() {
        val navigation = player.withCardNavigation()
        assertTrue(navigation.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(navigation.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        navigation.seekToNext()
        navigation.seekToPrevious()
        navigation.seekToNextMediaItem()
        navigation.seekToPreviousMediaItem()
        assertEquals(listOf("next", "previous", "next", "previous"), commands)
    }

    @Test
    fun `media item navigation commands are declared next to plain seek commands`() {
        val navigation = player.withCardNavigation()
        assertTrue(navigation.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(navigation.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(navigation.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun `play refused by the session keeps the player paused`() {
        controls.allowPlay = false
        player.play()
        assertFalse(player.isPlaying)
        assertEquals(listOf("play"), commands)
    }

    @Test
    fun `stop is routed to the session`() {
        player.play()
        commands.clear()
        player.stop()
        assertEquals(listOf("stop"), commands)
        assertFalse(player.isPlaying)
    }

    @Test
    fun `state updates do not trigger user commands`() {
        player.update(SessionPlayback("Card 1", "Deck", "1/5", true))
        player.update(SessionPlayback("Card 2", "Deck", "2/5", true))
        player.update(SessionPlayback("Card 2", "Deck", "2/5", false))
        assertFalse(player.isPlaying)
        assertTrue(commands.isEmpty())
    }
}
