package com.raycoll.app.playback

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Wywołania feedbacku są bezpieczne także zanim SoundPool zakończy ładowanie. */
@RunWith(RobolectricTestRunner::class)
class HapticAndSoundPlayerTest {
    @Test
    fun `repeated feedback and all ratings can be invoked without crashing`() {
        val player = HapticAndSoundPlayer(ApplicationProvider.getApplicationContext())
        repeat(2) {
            player.playSuccess()
            player.playError()
            for (rating in 0..4) player.haptic(rating)
        }
    }
}
