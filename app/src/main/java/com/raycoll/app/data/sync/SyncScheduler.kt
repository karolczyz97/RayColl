package com.raycoll.app.data.sync

import com.raycoll.app.domain.model.StudyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Polityka wysyłki do chmury (port persistenceQueue.ts + useStorePersistence.ts):
 * - debounced: zapis po [delayMs] od ostatniej mutacji (latest-wins — push czyta
 *   świeży snapshot z Room, więc nowsze mutacje same wygrywają),
 * - study: licznik ocen, flush natychmiast co [flushThreshold] ocen,
 * - immediate: flush od razu (import/usunięcie/archiwizacja/sign-out),
 * - single-in-flight (mutex) + retry: nieudany push zostawia flagę pending,
 *   następny trigger ponawia.
 *
 * Room jest zapisywany zawsze natychmiast niezależnie od tej polityki —
 * scheduler steruje wyłącznie zapisem do chmury.
 */
class SyncScheduler(
    private val scope: CoroutineScope,
    private val push: suspend () -> Boolean,
    private val delayMs: Long = StudyConstants.PERSISTENCE_DEBOUNCE_MS,
    private val flushThreshold: Int = StudyConstants.STUDY_REVIEW_FLUSH_THRESHOLD,
) {
    private var timer: Job? = null
    private var pending = false
    private var studyReviewCount = 0
    private val inFlight = Mutex()

    fun enqueueDebounced() {
        pending = true
        timer?.cancel()
        timer = scope.launch {
            delay(delayMs)
            timer = null // wyzeruj PRZED flush — flush anuluje timer, a to jest nasz własny job
            flush()
        }
    }

    /** Ocena w trybie study: bez debounce, flush dopiero co [flushThreshold] ocen. */
    fun enqueueStudy() {
        pending = true
        studyReviewCount++
        if (studyReviewCount >= flushThreshold) {
            studyReviewCount = 0
            timer?.cancel()
            scope.launch { flush() }
        }
    }

    fun enqueueImmediate() {
        pending = true
        timer?.cancel()
        scope.launch { flush() }
    }

    /**
     * Wypycha pending snapshot (jeśli jest). Zeruje licznik study — granica sesji
     * i przejście w tło zawsze domykają zaległe oceny.
     */
    suspend fun flush() {
        timer?.cancel()
        inFlight.withLock {
            if (!pending) return
            pending = false
            studyReviewCount = 0
            val ok = try {
                push()
            } catch (e: Exception) {
                false
            }
            // nieudany push: zostaw do ponowienia przy następnym triggerze
            // (chyba że w międzyczasie przyszła nowa mutacja — pending już true)
            if (!ok) pending = true
        }
    }

    /** Porzuca zaplanowane wysyłki (sign-out / porzucenie migracji). */
    fun cancel() {
        timer?.cancel()
        pending = false
        studyReviewCount = 0
    }
}
