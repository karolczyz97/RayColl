package com.raycoll.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.raycoll.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val soundPool: SoundPool
    private val successId: Int
    private val errorId: Int

    init {
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attr).build()
        successId = soundPool.load(context, R.raw.success, 1)
        errorId = soundPool.load(context, R.raw.error, 1)
    }

    fun playSuccess() { soundPool.play(successId, 1.0f, 1.0f, 1, 0, 1.0f) }
    fun playError() { soundPool.play(errorId, 1.0f, 1.0f, 1, 0, 1.0f) }
}
