package com.raycoll.app.playback

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Częściowy wake lock z limitem czasu, odnawiany w połowie limitu, dopóki jest trzymany:
 * zapomniany lock sam wygasa, a trzymany przez dłuższą sesję nie. Bez liczenia odwołań —
 * [hold] i [release] można wołać wielokrotnie.
 */
class TimedWakeLock(
    private val context: Context,
    private val scope: CoroutineScope,
    private val tag: String,
    private val timeoutMs: Long
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewal: Job? = null

    fun hold() {
        if (wakeLock != null) return
        val lock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            ?.apply { setReferenceCounted(false) }
            ?: return
        wakeLock = lock
        renewal = scope.launch {
            while (isActive) {
                lock.acquire(timeoutMs)
                delay(timeoutMs / 2)
            }
        }
    }

    fun release() {
        renewal?.cancel()
        renewal = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
