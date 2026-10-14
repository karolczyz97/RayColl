package com.raycoll.app.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

private const val TAG = "SilentPlayback"
private const val SAMPLE_RATE = 8_000
private const val LOOP_FRAMES = SAMPLE_RATE / 2 // pół sekundy ciszy w pętli
private const val BYTES_PER_FRAME = 2 // PCM 16 bit, mono

/** Odtwarzacz ciszy (abstrakcja dla testów). */
interface SilentTrack {
    fun play()
    fun pause()
    fun release()
}

/**
 * Przyciski słuchawek i klawisze multimedialne Android kieruje do sesji tej aplikacji,
 * która ostatnio sama odtwarzała dźwięk. Mowę odtwarza proces systemowego silnika TTS,
 * więc bez tego system nie wybrałby sesji RayColl (dumpsys: „Media button session is null”)
 * i stuknięcie w słuchawkę nie sterowałoby nauką. Na czas trwającej sesji aplikacja
 * odtwarza ciszę — zapętlony, statyczny bufor (bez wątku, bez danych do zapisu).
 */
class SilentPlayback(private val createTrack: () -> SilentTrack = ::AudioTrackSilence) {
    private var track: SilentTrack? = null
    private var playing = false

    /** Sesja trwa — aplikacja jest aktywnym odtwarzaczem multimediów. */
    fun start() {
        if (playing) return
        val current = ensureTrack() ?: return
        playing = runCatching { current.play() }
            .onFailure { Log.w(TAG, "Cannot start silent track", it) }
            .isSuccess
    }

    private fun ensureTrack(): SilentTrack? = track ?: runCatching(createTrack)
        .onFailure { Log.w(TAG, "Cannot create silent track", it) }
        .getOrNull()
        ?.also { track = it }

    /** Pauza / koniec — cisza nie trzyma toru audio. */
    fun stop() {
        if (!playing) return
        playing = false
        runCatching { track?.pause() }
    }

    fun release() {
        playing = false
        runCatching { track?.release() }
        track = null
    }
}

private class AudioTrackSilence : SilentTrack {
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(LOOP_FRAMES * BYTES_PER_FRAME)
        .build()
        .also { track ->
            val silence = ByteArray(LOOP_FRAMES * BYTES_PER_FRAME)
            track.write(silence, 0, silence.size)
            track.setLoopPoints(0, LOOP_FRAMES, -1)
        }

    override fun play() = track.play()

    override fun pause() = track.pause()

    override fun release() = track.release()
}
