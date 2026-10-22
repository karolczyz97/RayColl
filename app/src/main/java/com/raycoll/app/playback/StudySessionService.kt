package com.raycoll.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.raycoll.app.MainActivity
import com.raycoll.app.R
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "raycoll_study_playback_channel"
private const val NOTIFICATION_ID = 2001
private const val WAKE_LOCK_TAG = "RayColl:StudySession"
private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

/**
 * Nauka bez patrzenia w ekran: trzyma aplikację przy życiu, gdy sesja gra w tle,
 * pokazuje powiadomienie multimedialne i przekazuje do sesji przyciski
 * (graj/pauza/następna/poprzednia). Kończy się sam, gdy sesja się skończy.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class StudySessionService : MediaSessionService() {
    @Inject lateinit var sessionHolder: StudySessionHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var player: SessionPlayer

    // dzięki niej przyciski słuchawek trafiają do tej sesji (patrz [SilentPlayback])
    private val silence = SilentPlayback()
    private var mediaSession: MediaSession? = null
    private val wakeLock = TimedWakeLock(this, scope, WAKE_LOCK_TAG, WAKE_LOCK_TIMEOUT_MS)

    private val controls: SessionControls get() = sessionHolder.controls

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        // ten sam id i kanał co powiadomienie startowe: Media3 podmienia je w miejscu, nie dodaje drugiego
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )
        player = SessionPlayer(Looper.getMainLooper(), controls)
        mediaSession = MediaSession.Builder(this, player.withCardNavigation())
            .setSessionActivity(openAppIntent())
            .setCallback(RemoteControlCallback())
            .build()
            .also(::addSession)
        scope.launch {
            sessionHolder.session.collectLatest { active ->
                if (active == null) {
                    stopSelf()
                } else {
                    followSession(active.engine, active.deckName)
                }
            }
        }
    }

    private suspend fun followSession(engine: SessionEngine, deckName: String) {
        combine(engine.state, engine.inBackground, engine.isPaused) { state, background, paused ->
            Triple(state, background, paused)
        }.collect { (state, inBackground, manuallyPaused) ->
            if (state.status == SessionStatus.FINISHED) {
                stopSelf()
                return@collect
            }
            val idle = sessionAudioIdle(state, inBackground, manuallyPaused)
            if (idle) {
                silence.stop()
            } else {
                silence.start()
            }
            holdCpuAwake(inBackground && !idle)
            player.update(sessionPlayback(state, deckName, idle, text(R.string.study_background_paused)))
        }
    }

    /** Procesor nie może zasnąć, gdy sesja gra przy wyłączonym ekranie; na pierwszym planie nie jest to potrzebne. */
    private fun holdCpuAwake(hold: Boolean) = if (hold) wakeLock.hold() else wakeLock.release()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Usunięcie aplikacji z ostatnich kończy sesję. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        controls.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock.release()
        silence.release()
        mediaSession?.release()
        mediaSession = null
        player.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** Powiadomienie startowe jest wymagane w ciągu 5 s od startForegroundService; potem przejmuje je Media3. */
    private fun startInForeground() {
        ensureNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(sessionHolder.session.value?.deckName ?: text(R.string.app_name))
        .setContentText(text(R.string.study_notification_text))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            text(R.string.study_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
            .apply {
                description = text(R.string.study_notification_channel_desc)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel) // ponowne utworzenie aktualizuje nazwę po zmianie języka
    }

    private fun text(@StringRes id: Int): String = ContextCompat.getString(this, id)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
