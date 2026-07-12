package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.AtomicStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup

interface SessionDeps {
    suspend fun ttsSpeak(text: String, language: String, rate: Float)
    suspend fun sttListen(language: String, timeoutMs: Long): SttResult
    fun feedbackSuccess()
    fun feedbackError()
    fun haptic(strength: Int)
}

data class SttResult(
    val text: String = "",
    val confidence: Int = 0,
    val error: String? = null,
    val skipped: Boolean = false,
)

data class SessionContext(
    val cards: List<Flashcard>,
    val steps: List<AtomicStep>,
    val group: FlashcardGroup,
    val deps: SessionDeps,
    val baseSrsStates: Map<String, com.raycoll.app.domain.model.SrsState>,
)

data class ReviewEntry(
    val groupId: String,
    val cardId: String,
    val rating: Int,
    val replaceFromBase: Boolean,
    val baseSrsState: com.raycoll.app.domain.model.SrsState = com.raycoll.app.domain.srs.createNewSrsState(),
)

data class SessionResult(
    val reviews: List<ReviewEntry>,
    val state: StudySessionState,
)
