package com.raycoll.app.playback

import android.app.Application
import android.app.NotificationManager
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.WaitStep
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [32, 33], qualifiers = "pl")
class StudySessionServiceTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @BindValue lateinit var sessionHolder: StudySessionHolder
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: SessionEngine

    @Before fun start() {
        sessionHolder = StudySessionHolder(
            app,
            { _, _ -> },
            { true },
            object : AudioFocusRequester {
                override fun request(onChange: (FocusChange) -> Unit) = true
                override fun abandon() {}
            },
            scope
        )
        val cards = listOf(Flashcard("card", listOf("front", "back"), com.raycoll.app.domain.model.SrsState()))
        val deck = FlashcardGroup("deck", "Deck", cards)
        engine = SessionEngine(
            deck,
            listOf(WaitStep(ms = 100_000), NextCardStep()),
            FakeSessionDeps(),
            FakeClock(),
            1f,
            scope
        )
        sessionHolder.prepare(engine, deck.name)
        engine.start(cards)
        sessionHolder.register(engine, deck.name, false)
        hilt.inject()
    }

    @After fun close() {
        sessionHolder.release(engine)
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `real service creates a localized channel and foreground notification then stops on finish`() {
        val controller = Robolectric.buildService(StudySessionService::class.java).create()
        val service = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel("raycoll_study_playback_channel")
        assertEquals(app.getString(R.string.study_notification_channel), channel.name.toString())
        assertTrue(shadowOf(service).lastForegroundNotification != null)
        assertEquals(1, service.sessions.size)
        engine.stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
        assertTrue(service.sessions.isEmpty())
    }

    @Test fun `destroying the real service releases its media session and wake lock`() {
        engine.setBackground(true)
        val controller = Robolectric.buildService(StudySessionService::class.java).create()
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        val power = app.getSystemService(PowerManager::class.java)
        val wakeLock = org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()
        assertTrue(wakeLock.isHeld)
        controller.destroy()
        assertFalse(wakeLock.isHeld)
        assertTrue(service.sessions.isEmpty())
    }
}
