package com.raycoll.app.playback

import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.di.ApplicationScope
import com.raycoll.app.domain.session.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Koniec sesji domyka wysyłkę ocen, także po ponowieniu nieudanego zapisu. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionEndSync @Inject constructor(
    private val holder: StudySessionHolder,
    private val reviewWrites: ReviewWriteQueue,
    private val syncLifecycle: SyncLifecycle,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            holder.session
                .flatMapLatest { active ->
                    active?.engine?.state?.map { it.status == SessionStatus.FINISHED } ?: flowOf(true)
                }
                .combine(reviewWrites.state) { sessionOver, writes ->
                    sessionOver && (writes.pending == 0 || writes.failed)
                }
                .distinctUntilChanged()
                .collect { sessionOver ->
                    // Najpierw zapis w bazie; nieudane oceny czekają w kolejce na ponowienie.
                    if (sessionOver) {
                        reviewWrites.awaitIdle()
                        syncLifecycle.pushNow()
                    }
                }
        }
    }
}
