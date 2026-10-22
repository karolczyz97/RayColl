package com.raycoll.app.playback

import android.app.Application
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
class TimedWakeLockTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val timeoutMs = 10 * 60 * 1000L
    private val wakeLock = TimedWakeLock(app, scope, "test:lock", timeoutMs)

    @After
    fun teardown() {
        scope.cancel()
    }

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun latest(): PowerManager.WakeLock? = ShadowPowerManager.getLatestWakeLock()

    @Test
    fun `hold acquires a partial lock and is not repeated`() {
        wakeLock.hold()
        idleFor(1)
        val first = latest()
        assertNotNull(first)
        assertTrue(first!!.isHeld)
        wakeLock.hold()
        idleFor(1)
        assertSame(first, latest())
    }

    @Test
    fun `lock is renewed at half of the timeout while held`() {
        wakeLock.hold()
        idleFor(1)
        val lock = latest()!!
        idleFor(timeoutMs / 2 + 1_000)
        assertTrue(lock.isHeld)
        // odnowienie bez liczenia odwołań: jedno release zwalnia lock
        wakeLock.release()
        assertFalse(lock.isHeld)
    }

    @Test
    fun `release frees the lock and stops the renewal`() {
        wakeLock.hold()
        idleFor(1)
        val lock = latest()!!
        wakeLock.release()
        assertFalse(lock.isHeld)
        idleFor(timeoutMs)
        assertFalse(lock.isHeld)
        wakeLock.release()
    }

    @Test
    fun `hold after release takes a new lock`() {
        wakeLock.hold()
        idleFor(1)
        val first = latest()!!
        wakeLock.release()
        wakeLock.hold()
        idleFor(1)
        val second = latest()!!
        assertNotSame(first, second)
        assertTrue(second.isHeld)
        org.junit.Assert.assertFalse(first.isHeld)
    }
}
