package com.raycoll.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Zmiana fokusu audio istotna dla sesji nauki. */
enum class FocusChange {
    /** Fokus wrócił (np. po rozmowie albo komunikacie nawigacji). */
    GAINED,

    /** Chwilowa utrata — system odda fokus, gdy inna aplikacja skończy. */
    LOST_TRANSIENT,

    /** Trwała utrata — inna aplikacja zaczęła grać na dłużej (np. muzyka). */
    LOST
}

/** Systemowy fokus audio (abstrakcja dla [SessionAudioFocus] i testów). */
interface AudioFocusRequester {
    /** True: fokus przyznany. Ponowne wywołanie przy trzymanym fokusie nic nie zmienia. */
    fun request(onChange: (FocusChange) -> Unit): Boolean

    fun abandon()
}

@Singleton
class AudioFocusManager @Inject constructor(@ApplicationContext context: Context) : AudioFocusRequester {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    override fun request(onChange: (FocusChange) -> Unit): Boolean {
        focusRequest?.let { return true }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        // MAY_DUCK: muzyka innej aplikacji tylko ścisza się na czas nauki zamiast stawać
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { focus ->
                when (focus) {
                    AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.GAINED)

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(FocusChange.LOST_TRANSIENT)

                    AudioManager.AUDIOFOCUS_LOSS -> onChange(FocusChange.LOST)

                    // LOSS_TRANSIENT_CAN_DUCK: system sam ścisza mowę (Android 8+), nie przerywamy
                    else -> Unit
                }
            }
            .build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focusRequest = request
        return granted
    }

    override fun abandon() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
