package com.raycoll.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.raycoll.app.MainActivity
import com.raycoll.app.domain.session.SessionStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StudySessionService : MediaSessionService() {
    @Inject lateinit var sessionHolder: StudySessionHolder
    @Inject lateinit var audioFocusManager: AudioFocusManager

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null
    private var engineJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val context = this

        // audio focus loss (telefon/inna apka audio) → pauza sesji
        audioFocusManager.requestFocus {
            sessionHolder.get()?.pause()
            player?.pause()
        }

        val exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.apply {
            setMediaItem(silentItem(null))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                false
            )
            prepare()
        }
        player = exoPlayer

        // Prev/next w notyfikacji sterują silnikiem sesji, nie playlistą
        val sessionPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    super.isCommandAvailable(command)

            override fun seekToNext() { sessionHolder.get()?.skipToNextCard() }
            override fun seekToPrevious() { sessionHolder.get()?.goToPreviousCard() }

            override fun play() {
                super.play()
                sessionHolder.get()?.resume()
            }

            override fun pause() {
                super.pause()
                sessionHolder.get()?.pause()
            }

            override fun stop() {
                sessionHolder.get()?.end()
                super.stop()
            }
        }

        val sessionActivityIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(context, sessionPlayer)
            .setSessionActivity(pendingIntent)
            .build()

        observeEngineState()

        // błąd TTS w tle → auto-pauza silnika + notyfikacja "wstrzymane"
        engineJob = scope.launch {
            sessionHolder.engineFlow.collect { engine ->
                engine?.onBackgroundAutoPause = { player?.pause() }
            }
        }
    }

    private fun silentItem(metadata: MediaMetadata?): MediaItem {
        val builder = MediaItem.Builder()
            .setUri("android.resource://$packageName/${com.raycoll.app.R.raw.success}")
        metadata?.let { builder.setMediaMetadata(it) }
        return builder.build()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeEngineState() {
        stateJob?.cancel()
        stateJob = scope.launch {
            sessionHolder.engineFlow
                .flatMapLatest { engine -> engine?.state ?: emptyFlow() }
                .collect { state ->
                    val player = this@StudySessionService.player ?: return@collect
                    val card = state.cards.getOrNull(state.currentCardIndex)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(card?.pages?.firstOrNull() ?: "")
                        .setArtist(sessionHolder.deckName() ?: "")
                        .setSubtitle("${state.currentCardIndex + 1}/${state.cards.size}")
                        .build()
                    player.replaceMediaItem(0, silentItem(metadata))

                    when (state.status) {
                        SessionStatus.FINISHED -> {
                            player.pause()
                            stopSelf()
                        }
                        // auto-pauza: wymagana interakcja lub błąd → notyfikacja "wstrzymane"
                        SessionStatus.REVEALED, SessionStatus.ERROR -> player.pause()
                        SessionStatus.SPEAKING, SessionStatus.LISTENING, SessionStatus.IDLE -> {
                            if (!player.playWhenReady) player.play()
                        }
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        sessionHolder.get()?.stop()
        sessionHolder.release()
        player?.pause()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        audioFocusManager.abandonFocus()
        sessionHolder.get()?.onBackgroundAutoPause = null
        engineJob?.cancel()
        stateJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
