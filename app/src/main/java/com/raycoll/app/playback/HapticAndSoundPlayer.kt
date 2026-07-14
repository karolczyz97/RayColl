package com.raycoll.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticAndSoundPlayer @Inject constructor(private val context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0

    init {
        // load przy konstrukcji — wcześniej loadSounds() nie było wołane nigdzie
        // i feedback dźwiękowy był no-opem (id=0)
        try { successSoundId = soundPool.load(context, com.raycoll.app.R.raw.success, 1) } catch (_: Exception) {}
        try { errorSoundId = soundPool.load(context, com.raycoll.app.R.raw.error, 1) } catch (_: Exception) {}
    }

    // dźwięk + haptyka razem — parytet z playSuccessSound()+playSuccessHaptic()
    // w stepExecutor.ts (feedback_success / feedback_error)
    fun playSuccess() {
        if (successSoundId != 0) soundPool.play(successSoundId, 1f, 1f, 1, 0, 1f)
        vibrate(20L)
    }

    fun playError() {
        if (errorSoundId != 0) soundPool.play(errorSoundId, 1f, 1f, 1, 0, 1f)
        vibrate(50L)
    }

    fun haptic(rating: Int) {
        val duration = when (rating) {
            1 -> 50L; 2 -> 30L; 3 -> 20L; 4 -> 40L; else -> 20L
        }
        vibrate(duration)
    }

    private fun vibrate(duration: Long) {
        val vibrator = this.vibrator ?: return
        if (!vibrator.hasVibrator()) return
        // minSdk 26 (O) — createOneShot dostępny bezwarunkowo
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() { soundPool.release() }
}
