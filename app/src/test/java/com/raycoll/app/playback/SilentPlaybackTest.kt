package com.raycoll.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SilentPlaybackTest {

    private class FakeTrack(private val calls: MutableList<String>) : SilentTrack {
        override fun play() {
            calls += "play"
        }
        override fun pause() {
            calls += "pause"
        }
        override fun release() {
            calls += "release"
        }
    }

    @Test
    fun `silence plays while the session runs and stops on pause without recreating the track`() {
        val calls = mutableListOf<String>()
        var created = 0
        val silence = SilentPlayback {
            created++
            FakeTrack(calls)
        }
        silence.start()
        silence.start() // kolejne aktualizacje stanu sesji nie startują drugi raz
        silence.stop()
        silence.stop()
        silence.start()
        silence.release()
        assertEquals(listOf("play", "pause", "play", "release"), calls)
        assertEquals(1, created)
    }

    @Test
    fun `failure to create the track does not break the session`() {
        val silence = SilentPlayback { error("no audio output") }
        silence.start()
        silence.stop()
        silence.release()
    }

    @Test
    fun `real AudioTrack loop can be started, paused and released`() {
        val silence = SilentPlayback()
        silence.start()
        silence.stop()
        silence.start()
        silence.release()
    }
}
