package com.raycoll.app.playback

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformAudioFocusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val platform = shadowOf(context.getSystemService(AudioManager::class.java))
    private val focus = AudioFocusManager(context)

    @Test fun `focus can be denied retried and abandoned on the platform`() {
        platform.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(focus.request {})
        platform.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertTrue(focus.request {})
        focus.abandon()
        assertNotNull(platform.lastAbandonedAudioFocusRequest)
        assertTrue(platform.lastAbandonedAudioFocusRequest.willPauseWhenDucked())
    }

    @Test fun `platform listener maps ducking and permanent focus loss`() {
        val changes = mutableListOf<FocusChange>()
        assertTrue(focus.request(changes::add))
        val listener = platform.lastAudioFocusRequest.listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(listOf(FocusChange.LOST_TRANSIENT, FocusChange.GAINED, FocusChange.LOST), changes)
    }

    @Test fun `late callbacks from yielded or replaced focus cannot pause a recognition turn`() {
        val changes = mutableListOf<FocusChange>()
        assertTrue(focus.request(changes::add))
        val stale = platform.lastAudioFocusRequest.listener
        focus.abandon()
        stale.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        stale.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(changes.isEmpty())
        assertTrue(focus.request(changes::add))
        stale.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertTrue(changes.isEmpty())
        platform.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(listOf(FocusChange.LOST_TRANSIENT), changes)
    }
}
