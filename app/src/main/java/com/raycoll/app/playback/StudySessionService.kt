package com.raycoll.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.raycoll.app.MainActivity
import com.raycoll.app.R
import com.raycoll.app.domain.session.InteractionGateKind
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
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

    @Inject lateinit var audioFocusManager: AudioFocusManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var player: SessionPlayer
    private lateinit var focus: SessionAudioFocus

    // dzięki niej przyciski słuchawek trafiają do tej sesji (patrz [SilentPlayback])
    private val silence = SilentPlayback()
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewal: Job? = null

    private val focusedSession = object : FocusedSession {
        override fun resume() {
            sessionHolder.current()?.resume()
        }

        override fun pause() {
            sessionHolder.current()?.pause()
        }
    }

    private val controls = object : SessionControls {
        override fun play(): Boolean = focus.play()

        override fun pause() = focus.pause()

        override fun next() {
            sessionHolder.current()?.skipToNextCard()
        }

        override fun previous() {
            sessionHolder.current()?.goToPreviousCard()
        }

        override fun stop() {
            sessionHolder.current()?.let(sessionHolder::release)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )
        player = SessionPlayer(Looper.getMainLooper(), controls)
        // fokus audio idzie za stanem sesji (pauza go oddaje), utrata fokusu wstrzymuje sesję
        focus = SessionAudioFocus(audioFocusManager, focusedSession)
        sessionHolder.attachControls(controls)
        mediaSession = MediaSession.Builder(this, player.withCardNavigation(controls))
            .setSessionActivity(openAppIntent())
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

    private suspend fun followSession(engine: SessionEngine, deckName: String) = coroutineScope {
        launch {
            // sesja, która stanęła w tle, czeka na użytkownika
            engine.autoPausedInBackground.collect {
                releaseWakeLock()
                player.update(playbackOf(engine.state.value, deckName, playing = false, paused = true))
            }
        }
        launch {
            combine(engine.state, engine.inBackground, engine.isPaused) { state, background, paused ->
                Triple(state, background, paused)
            }.collect { (state, inBackground, manuallyPaused) ->
                if (state.status == SessionStatus.FINISHED) {
                    stopSelf()
                    return@collect
                }
                val paused = sessionAudioIdle(state, inBackground, manuallyPaused)
                if (paused) {
                    focus.onSessionPaused()
                    silence.stop()
                } else {
                    focus.onSessionPlaying()
                    silence.start()
                }
                holdCpuAwake(inBackground && !paused)
                player.update(playbackOf(state, deckName, playing = !paused, paused = paused))
            }
        }
    }

    private fun playbackOf(state: SessionState, deckName: String, playing: Boolean, paused: Boolean): SessionPlayback {
        val card = state.cards.getOrNull(state.currentCardIndex)
        val progress = if (paused) {
            getString(
                R.string.study_background_paused
            )
        } else {
            "${state.currentCardIndex + 1}/${state.cards.size}"
        }
        return SessionPlayback(
            title = card?.pages?.firstOrNull().orEmpty(),
            deckName = deckName,
            progress = progress,
            playing = playing
        )
    }

    /** Procesor nie może zasnąć, gdy sesja gra przy wyłączonym ekranie; na pierwszym planie nie jest to potrzebne. */
    private fun holdCpuAwake(hold: Boolean) {
        if (hold && wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                ?.apply { setReferenceCounted(false) }
            wakeLock?.let { lock ->
                wakeLockRenewal = scope.launch {
                    while (isActive) {
                        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
                        delay(WAKE_LOCK_TIMEOUT_MS / 2)
                    }
                }
            }
        } else if (!hold) {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLockRenewal?.cancel()
        wakeLockRenewal = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Usunięcie aplikacji z ostatnich kończy sesję. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        controls.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        sessionHolder.detachControls(controls)
        scope.cancel()
        releaseWakeLock()
        silence.release()
        focus.release()
        mediaSession?.release()
        mediaSession = null
        player.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startInForeground() {
        ensureNotificationChannel()
        val type = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(sessionHolder.session.value?.deckName ?: getString(R.string.app_name))
        .setContentText(getString(R.string.study_notification_text))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.study_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
            .apply {
                description = getString(R.string.study_notification_channel_desc)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel) // ponowne utworzenie aktualizuje nazwę po zmianie języka
    }

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

/**
 * Sesja nic nie odtwarza: pauza albo czekanie na użytkownika (błąd, odsłonięte oceny, tap na
 * pierwszym planie). Wtedy oddajemy fokus audio i wyłączamy ciszę, żeby nie trzymać muzyki/nawigacji.
 * W tle bramka tapu nie czeka — sesja bez rąk idzie dalej.
 */
internal fun sessionAudioIdle(state: SessionState, inBackground: Boolean, manuallyPaused: Boolean): Boolean =
    manuallyPaused ||
        state.status == SessionStatus.ERROR ||
        state.status == SessionStatus.REVEALED ||
        (!inBackground && state.interactionGate.kind != InteractionGateKind.NONE)
