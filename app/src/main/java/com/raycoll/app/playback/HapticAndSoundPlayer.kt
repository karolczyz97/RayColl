package com.raycoll.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.raycoll.app.R
import com.raycoll.app.domain.model.Ratings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val SUCCESS_VIBRATION_MS = 20L
private const val ERROR_VIBRATION_MS = 50L

/** Czas wibracji po ocenie karty. */
internal fun ratingVibrationMs(rating: Int): Long = when (rating) {
    Ratings.AGAIN -> 50L
    Ratings.HARD -> 30L
    Ratings.GOOD -> 20L
    Ratings.EASY -> 40L
    else -> 20L
}

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

    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    // dźwięk zamówiony, zanim skończył się ładować, gra zaraz po załadowaniu
    private val loaded = mutableSetOf<Int>()
    private val pending = mutableSetOf<Int>()

    private val successSound: Int
    private val errorSound: Int

    init {
        // słuchacz przed load(): inaczej szybkie załadowanie mogłoby przejść niezauważone
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status != 0) {
                Log.w("HapticAndSoundPlayer", "Cannot load sound $soundId (status $status)")
                synchronized(this) { pending.remove(soundId) }
                return@setOnLoadCompleteListener
            }
            val playNow = synchronized(this) {
                loaded += soundId
                pending.remove(soundId)
            }
            if (playNow) playOnce(soundId)
        }
        successSound = soundPool.load(context, R.raw.success, 1)
        errorSound = soundPool.load(context, R.raw.error, 1)
    }

    fun playSuccess() {
        play(successSound)
        vibrate(SUCCESS_VIBRATION_MS)
    }

    fun playError() {
        play(errorSound)
        vibrate(ERROR_VIBRATION_MS)
    }

    fun haptic(rating: Int) = vibrate(ratingVibrationMs(rating))

    private fun play(soundId: Int) {
        val ready = synchronized(this) {
            (soundId in loaded).also { if (!it) pending += soundId }
        }
        if (ready) playOnce(soundId)
    }

    private fun playOnce(soundId: Int) {
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    private fun vibrate(durationMs: Long) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
