package com.raycoll.app.playback

import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Co pokazać w powiadomieniu i na ekranie blokady. */
data class SessionPlayback(val title: String, val deckName: String, val progress: String, val playing: Boolean)

/** Polecenia z powiadomienia / słuchawek przekazywane do sesji nauki. */
interface SessionControls {
    /** False: sesja nie mogła ruszyć (np. odmowa fokusu audio) — przycisk zostaje na „graj”. */
    fun play(): Boolean

    fun pause()
    fun next()
    fun previous()
    fun stop()
}

/**
 * "Odtwarzacz" bez własnego dźwięku: przedstawia sesję nauki systemowi (powiadomienie
 * multimedialne, ekran blokady, słuchawki). Mowę odtwarza TTS, nie ten obiekt.
 * Następna/poprzednia karta: [withCardNavigation] — playlista ma jeden element,
 * więc zwykłe przewijanie byłoby przez Media3 ignorowane.
 */
@UnstableApi
class SessionPlayer(looper: Looper, private val controls: SessionControls) : SimpleBasePlayer(looper) {
    private var playback = SessionPlayback(title = "", deckName = "", progress = "", playing = false)

    fun update(value: SessionPlayback) {
        if (value == playback) return
        playback = value
        invalidateState()
    }

    override fun getState(): State {
        val metadata = MediaMetadata.Builder()
            .setTitle(playback.title)
            .setArtist(playback.deckName)
            .setSubtitle(playback.progress)
            .build()
        val item = MediaItemData.Builder(ITEM_UID)
            .setMediaItem(MediaItem.Builder().setMediaId(ITEM_UID).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .build()
        return State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlaylist(listOf(item))
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playback.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val playing = if (playWhenReady) {
            controls.play()
        } else {
            controls.pause()
            false
        }
        playback = playback.copy(playing = playing)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playback = playback.copy(playing = false)
        controls.stop()
        return Futures.immediateVoidFuture()
    }

    /** Przycisk "następny/poprzedni" przełącza karty sesji. */
    fun withCardNavigation(): Player = object : ForwardingPlayer(this) {
        override fun seekToNext() = controls.next()

        override fun seekToNextMediaItem() = controls.next()

        override fun seekToPrevious() = controls.previous()

        override fun seekToPreviousMediaItem() = controls.previous()
    }

    private companion object {
        const val ITEM_UID = "study-session"
        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                // obie pary przełączają kartę — obsługuje je [withCardNavigation]
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA
            )
            .build()
    }
}
