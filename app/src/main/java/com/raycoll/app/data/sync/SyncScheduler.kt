package com.raycoll.app.data.sync

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// zmiany czekają chwilę, żeby seria edycji poszła jedną wysyłką; w nauce wysyłka co kilka ocen
private const val SYNC_DEBOUNCE_MS = 1_200L
private const val STUDY_FLUSH_EVERY_REVIEWS = 10

/**
 * Kiedy wysyłać zmiany do chmury (Room zapisuje się zawsze od razu):
 * - debounced: [delayMs] po ostatniej zmianie,
 * - study: co [flushThreshold] ocen,
 * - immediate: od razu.
 * Naraz trwa najwyżej jedna wysyłka; nieudana zostaje do ponowienia przy następnej okazji.
 * Wysyłka zawsze czyta aktualny stan, więc nowsze zmiany same "wygrywają".
 */
class SyncScheduler(
    private val scope: CoroutineScope,
    private val push: suspend () -> Boolean,
    private val delayMs: Long = SYNC_DEBOUNCE_MS,
    private val flushThreshold: Int = STUDY_FLUSH_EVERY_REVIEWS
) {
    private val pending = AtomicBoolean(false)
    private val studyReviewCount = AtomicInteger(0)
    private val inFlight = Mutex()
    private var timer: Job? = null
    private val running = AtomicReference<Job?>(null)

    fun enqueueDebounced() {
        pending.set(true)
        restartTimer()
    }

    fun enqueueStudy() {
        pending.set(true)
        if (studyReviewCount.incrementAndGet() >= flushThreshold) flushNow()
    }

    fun enqueueImmediate() {
        pending.set(true)
        flushNow()
    }

    /**
     * Wysyła zaległe zmiany (jeśli są). Zeruje licznik ocen — koniec sesji domyka wszystko.
     * Zwraca false, gdy wysyłka się nie udała (zmiany czekają na ponowienie).
     */
    suspend fun flush(): Boolean {
        cancelTimer()
        return inFlight.withLock {
            if (!pending.getAndSet(false)) return true
            studyReviewCount.set(0)
            var ok = false
            // wysyłka w osobnym dziecku: [abort] przerywa tylko ją, nie korutynę wołającego
            val aborted = try {
                coroutineScope {
                    val job = launch {
                        ok = try {
                            push()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false
                        }
                    }
                    running.set(job)
                    job.join()
                    job.isCancelled
                }
            } catch (e: CancellationException) {
                pending.set(true)
                throw e
            } finally {
                running.set(null)
            }
            // przerwana przez [abort] zostaje porzucona razem z resztą kolejki
            if (!ok && !aborted) pending.set(true)
            ok
        }
    }

    /**
     * Wysyłka w bieżącej korutynie, bez dodatkowej w tle — przerwanie wołającego
     * (limit wylogowania) nie zostawia w kolejce drugiej wysyłki, która znowu by czekała.
     */
    suspend fun flushImmediately(): Boolean {
        pending.set(true)
        return flush()
    }

    /** Praca z chmurą, która nie może iść równolegle z wysyłką (uzgodnienie po powrocie do aplikacji). */
    suspend fun <T> exclusive(block: suspend () -> T): T = inFlight.withLock { block() }

    /** Porzuca zaplanowane wysyłki i przerywa trwającą (wylogowanie po limicie czasu). */
    fun abort() {
        cancel()
        running.get()?.cancel()
    }

    /** Porzuca zaplanowane wysyłki (wylogowanie, rezygnacja z migracji). */
    fun cancel() {
        cancelTimer()
        pending.set(false)
        studyReviewCount.set(0)
    }

    private fun flushNow() {
        cancelTimer()
        scope.launch { flush() }
    }

    @Synchronized
    private fun restartTimer() {
        timer?.cancel()
        timer = scope.launch {
            delay(delayMs)
            // odłącz się, zanim flush anuluje timer — inaczej anulowałby samego siebie
            detachTimer(coroutineContext[Job])
            flush()
        }
    }

    @Synchronized
    private fun detachTimer(job: Job?) {
        if (timer === job) timer = null
    }

    @Synchronized
    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }
}
