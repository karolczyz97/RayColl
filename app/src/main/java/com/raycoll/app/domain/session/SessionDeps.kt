package com.raycoll.app.domain.session

interface SessionDeps {
    suspend fun speak(text: String, lang: String, rate: Float): SpeakResult
    suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult
    fun feedbackSuccess()
    fun feedbackError()
    fun haptic(rating: Int)
    fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: com.raycoll.app.domain.model.SrsState?)
}

enum class SpeakResult { SUCCESS, ERROR, MISSING_VOICE }

data class ListenResult(
    val text: String,
    val status: ListenStatus,
)

enum class ListenStatus { COMPLETED, SKIPPED, ERROR }
