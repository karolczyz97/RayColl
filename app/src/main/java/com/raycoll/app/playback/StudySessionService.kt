package com.raycoll.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StudySessionService : MediaSessionService() {
    @Inject lateinit var sessionHolder: StudySessionHolder

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val context = this

        val exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.apply {
            val silentItem = MediaItem.Builder()
                .setUri("android.resource://$packageName/${com.raycoll.app.R.raw.success}")
                .build()
            setMediaItem(silentItem)
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
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    val engine = sessionHolder.get() ?: return
                    if (playWhenReady) engine.resume() else engine.pause()
                }
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                        val engine = sessionHolder.get() ?: return
                        when {
                            player.hasNextMediaItem() -> engine.skipToNextCard()
                            player.hasPreviousMediaItem() -> engine.goToPreviousCard()
                        }
                    }
                }
            })
        }
        player = exoPlayer

        val sessionActivityIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(context, player!!)
            .setSessionActivity(pendingIntent)
            .build()

        observeEngineState()
    }

    private fun observeEngineState() {
        stateJob?.cancel()
        stateJob = scope.launch {
            sessionHolder.let { holder ->
                val engine = holder.get() ?: return@launch
                engine.state.collectLatest { state ->
                    val player = this@StudySessionService.player ?: return@collectLatest
                    val card = state.cards.getOrNull(state.currentCardIndex)
                    val title = card?.pages?.firstOrNull() ?: ""
                    val subtitle = holder.deckName() ?: ""

                    val metadata = MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .setSubtitle("${state.currentCardIndex + 1}/${state.cards.size}")
                        .build()

                    player.let { p ->
                        p.replaceMediaItem(0, MediaItem.Builder()
                            .setMediaMetadata(metadata)
                            .setUri("android.resource://$packageName/${com.raycoll.app.R.raw.success}")
                            .build())
                    }

                    when (state.status) {
                        SessionStatus.FINISHED -> { player.pause(); stopSelf() }
                        SessionStatus.REVEALED, SessionStatus.ERROR -> player.pause()
                        SessionStatus.SPEAKING, SessionStatus.LISTENING, SessionStatus.IDLE -> {
                            if (!player.playWhenReady) player.play()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val engine = sessionHolder.get()
        engine?.stop()
        sessionHolder.release()
        player?.pause()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stateJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
