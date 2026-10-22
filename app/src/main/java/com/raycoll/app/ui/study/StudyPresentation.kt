package com.raycoll.app.ui.study

import com.raycoll.app.domain.session.AnswerStatus
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus

internal enum class StudyActionKind { RATINGS, REVEAL, NONE }

internal data class StudyActionState(
    val kind: StudyActionKind,
    val enabled: Boolean = false,
    val suggested: Int? = null
)

/** Pod kartą: oceny po odsłonięciu, wcześniej odkrywanie kolejnej strony, a w trakcie nasłuchu nic. */
internal fun studyActionState(session: SessionState, pageCount: Int): StudyActionState {
    val canReveal = session.status != SessionStatus.LISTENING &&
        session.revealedPages.count { it < pageCount } < pageCount &&
        !session.cardReviewed && session.lastAnswerResult.status == AnswerStatus.NONE
    return when {
        session.status == SessionStatus.AWAITING_RATING -> StudyActionState(
            StudyActionKind.RATINGS,
            !session.cardReviewed || session.revisiting,
            session.lastAnswerResult.suggestedRating
        )

        canReveal -> StudyActionState(StudyActionKind.REVEAL)

        else -> StudyActionState(StudyActionKind.NONE)
    }
}

internal data class AnswerPanelState(
    val visible: Boolean,
    val answer: LastAnswerResult,
    val listening: Boolean,
    /** Słowo „Słucham” przy fali, gdy słuchana strona jest zakryta. */
    val listeningLabel: Boolean
)

/**
 * Panel odpowiedzi: w trakcie nasłuchu i po wyniku bieżącej karty, ale nie gdy karta czyta pytanie.
 * Po zmianie karty znikający panel pokazuje jeszcze wynik poprzedniej karty.
 */
internal fun answerPanelState(session: SessionState): AnswerPanelState {
    val listening = session.status == SessionStatus.LISTENING
    val readingQuestion = session.status == SessionStatus.SPEAKING && session.audioPageIndex == 0
    val hasAnswer = session.lastAnswerResult.status != AnswerStatus.NONE
    return AnswerPanelState(
        visible = (listening || hasAnswer) && !readingQuestion,
        answer = if (hasAnswer || listening) session.lastAnswerResult else session.previousAnswerResult,
        listening = listening,
        listeningLabel = listening && session.audioPageIndex !in session.revealedPages
    )
}
