package com.raycoll.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.raycoll.app.R
import com.raycoll.app.domain.model.Ratings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Dźwięki i wibracje informacji zwrotnej w sesji nauki. Żyje tyle co proces. */
@Singleton
class HapticAndSoundPlayer @Inject constructor(@ApplicationContext context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    // dźwięk zamówiony, zanim skończył się ładować, gra zaraz po załadowaniu
    private val loaded = mutableSetOf<Int>()
    private val pending = mutableSetOf<Int>()

    private val successSound: Int
    private val errorSound: Int

    init {
        // słuchacz przed load(): inaczej szybkie załadowanie mogłoby przejść niezauważone
        soundPool.setOnLoadCompleteListener { pool, soundId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val playNow = synchronized(this) {
                loaded += soundId
                pending.remove(soundId)
            }
            if (playNow) pool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
        successSound = soundPool.load(context, R.raw.success, 1)
        errorSound = soundPool.load(context, R.raw.error, 1)
    }

    fun playSuccess() {
        play(successSound)
        vibrate(20L)
    }

    fun playError() {
        play(errorSound)
        vibrate(50L)
    }

    fun haptic(rating: Int) {
        vibrate(
            when (rating) {
                Ratings.AGAIN -> 50L
                Ratings.HARD -> 30L
                Ratings.EASY -> 40L
                else -> 20L
            }
        )
    }

    private fun play(soundId: Int) {
        val ready = synchronized(this) {
            (soundId in loaded).also { if (!it) pending += soundId }
        }
        if (ready) soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    private fun vibrate(durationMs: Long) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
