package com.raycoll.app.data.sync

import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.StudyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    private val syncEngine: SyncEngine,
    private val deckRepository: DeckRepository,
) {
    private var debounceJob: Job? = null
    private var studyReviewCount = 0

    fun scheduleDebounced(scope: CoroutineScope) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(StudyConstants.PERSISTENCE_DEBOUNCE_MS)
            syncEngine.status
        }
    }

    fun onStudyReview(scope: CoroutineScope) {
        studyReviewCount++
        if (studyReviewCount >= StudyConstants.STUDY_REVIEW_FLUSH_THRESHOLD) {
            studyReviewCount = 0
            scheduleDebounced(scope)
        }
    }

    fun flushNow(scope: CoroutineScope) {
        debounceJob?.cancel()
        studyReviewCount = 0
        scope.launch { /* sync */ }
    }
}
