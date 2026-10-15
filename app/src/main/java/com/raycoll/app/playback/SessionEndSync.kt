package com.raycoll.app.playback

import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Koniec sesji domyka wysyłkę ocen, także po ponowieniu nieudanego zapisu. */
@Singleton
class SessionEndSync @Inject constructor(
    private val holder: StudySessionHolder,
    private val reviewWrites: ReviewWriteQueue,
    private val syncLifecycle: SyncLifecycle,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            holder.boundary
                .combine(reviewWrites.state) { boundary, writes ->
                    boundary.attempt.takeIf {
                        boundary.ended && writes.pending == 0 && !writes.failed
                    }
                }
                .distinctUntilChanged()
                .collect { completed ->
                    // Scheduler serializuje wysyłki; obserwator nadal odbiera następne zakończenia.
                    if (completed != null) scope.launch { syncLifecycle.pushNow() }
                }
        }
    }
}
