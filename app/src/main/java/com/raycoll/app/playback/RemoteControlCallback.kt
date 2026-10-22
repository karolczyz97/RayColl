package com.raycoll.app.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Od Media3 1.11 aplikacje spoza systemu (np. sterowanie multimediami na zegarku) dostają
 * domyślnie tylko odczyt: widzą tytuł i stan, ale przyciski są nieaktywne. Sesja nauki
 * udostępnia wszystkim pełne sterowanie odtwarzaniem — pauza czy zmiana karty nic nie ujawnia.
 */
@OptIn(UnstableApi::class)
class RemoteControlCallback : MediaSession.Callback {
    override fun onConnectAsync(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.ConnectionResult> = Futures.immediateFuture(
        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .build()
    )
}
