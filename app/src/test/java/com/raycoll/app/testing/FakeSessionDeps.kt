package com.raycoll.app.testing

import com.raycoll.app.data.repository.PendingReview
import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.session.ListenResult
import com.raycoll.app.domain.session.ListenStatus
import com.raycoll.app.domain.session.SessionDeps
import com.raycoll.app.domain.session.SpeakResult
import kotlinx.coroutines.awaitCancellation

data class ReviewRecord(val cardId: String, val rating: Int, val replaceFromBase: SrsState?)

/** Atrapa mowy, nasłuchu i ocen; opcjonalna kolejka sprawdza także rzeczywisty zapis. */
class FakeSessionDeps(
    var speakResult: SpeakResult = SpeakResult.SUCCESS,
    var listenHangs: Boolean = false,
    var transcript: String = "",
    var listenStatus: ListenStatus = ListenStatus.COMPLETED,
    private val reviewWrites: ReviewWriteQueue? = null,
    var listenThrows: Boolean = false
) : SessionDeps {
    val reviews = mutableListOf<ReviewRecord>()
    val spoken = mutableListOf<String>()
    override suspend fun speak(text: String, lang: String, rate: Float): SpeakResult {
        spoken += text
        return speakResult
    }
    override suspend fun listen(lang: String, timeoutMs: Long, onPartial: (String) -> Unit): ListenResult {
        if (listenHangs) awaitCancellation()
        if (listenThrows) throw IllegalStateException("stt crashed")
        return ListenResult(transcript, listenStatus)
    }
    override fun feedbackSuccess() {}
    override fun feedbackError() {}
    override fun haptic(rating: Int) {}
    override fun onCardReviewed(deckId: String, cardId: String, rating: Int, replaceFromBase: SrsState?) {
        reviews += ReviewRecord(cardId, rating, replaceFromBase)
        reviewWrites?.submit(PendingReview(deckId, cardId, rating, replaceFromBase))
    }
}
