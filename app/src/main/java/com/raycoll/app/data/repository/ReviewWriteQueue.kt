package com.raycoll.app.data.repository

import android.util.Log
import com.raycoll.app.di.ApplicationScope
import com.raycoll.app.domain.model.SrsState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ReviewWriteQueue"

/** Ocena karty czekająca na zapis. [replaceFromBase] != null: ponowna ocena liczona od stanu sprzed pierwszej. */
data class PendingReview(
    val deckId: String,
    val cardId: String,
    val rating: Int,
    val replaceFromBase: SrsState?,
    val namespace: String = NamespaceManager.GUEST
)

/** [pending] — oceny jeszcze niezapisane; [failed] — zapis się nie udał i czeka na ponowienie. */
data class ReviewQueueState(val pending: Int = 0, val failed: Boolean = false)

/**
 * Zapis ocen poza zakresem ekranu: wyjście z sesji zaraz po ocenie nie może jej zgubić.
 * Oceny zapisują się po kolei (poprawiona ocena nie wyprzedzi pierwszej), a [awaitIdle]
 * pozwala poczekać, aż wszystkie zgłoszone oceny będą w bazie — przed ponowną sesją
 * i przed końcową wysyłką do chmury. Nieudany zapis zostaje w kolejce (z kolejnymi
 * ocenami za nim) do ponowienia i jest widoczny w [state].
 */
@Singleton
class ReviewWriteQueue internal constructor(
    private val scope: CoroutineScope,
    private val currentNamespace: () -> String = { NamespaceManager.GUEST },
    private val write: suspend (PendingReview) -> Unit
) {
    @Inject
    constructor(
        deckRepository: DeckRepository,
        @ApplicationScope scope: CoroutineScope,
        namespaceManager: NamespaceManager
    ) : this(
        scope,
        namespaceManager::current,
        { review ->
            deckRepository.reviewCard(
                review.deckId,
                review.cardId,
                review.rating,
                review.replaceFromBase,
                review.namespace
            )
        }
    )

    private val queue = ArrayDeque<PendingReview>()
    private var worker: Job? = null
    private var failed = false

    private val _state = MutableStateFlow(ReviewQueueState())
    val state: StateFlow<ReviewQueueState> = _state.asStateFlow()

    /** Zgłasza ocenę do zapisu. Po wcześniejszym błędzie ponawia też zaległe oceny (w kolejności). */
    fun submit(review: PendingReview) {
        synchronized(queue) {
            queue.addLast(review.copy(namespace = currentNamespace()))
            failed = false
            publish()
        }
        ensureWorker()
    }

    /** Ponawia zapis ocen, które się nie zapisały. */
    fun retry() {
        synchronized(queue) {
            failed = false
            publish()
        }
        ensureWorker()
    }

    /**
     * Czeka, aż wszystkie zgłoszone oceny będą zapisane (true) albo zapis się nie uda (false —
     * oceny zostają w kolejce). Nie blokuje wątku.
     */
    suspend fun awaitIdle(): Boolean = state.first { it.pending == 0 || it.failed }.pending == 0

    private fun ensureWorker() = synchronized(queue) {
        if (worker != null || failed || queue.isEmpty()) return@synchronized
        // Rejestracja przed startem: natychmiastowy sukces/błąd nie zostawia zakończonego joba w worker.
        worker = scope.launch(start = CoroutineStart.LAZY) { drain() }
        worker?.start()
    }

    private suspend fun drain() {
        while (true) {
            val next = synchronized(queue) {
                queue.firstOrNull() ?: run {
                    worker = null
                    null
                }
            } ?: return
            try {
                write(next)
            } catch (e: CancellationException) {
                synchronized(queue) { worker = null }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving review of card ${next.cardId} failed", e)
                synchronized(queue) {
                    failed = true
                    worker = null
                    publish()
                }
                return
            }
            synchronized(queue) {
                queue.removeFirst()
                publish()
            }
        }
    }

    private fun publish() {
        _state.value = ReviewQueueState(pending = queue.size, failed = failed)
    }
}
