package com.raycoll.app.domain.session

/** Stan odwiedzonej karty w tej próbie. Nie przechowuje audio, zadań ani chwilowego podglądu. */
data class CardProgress(
    val revealedPages: List<Int>,
    val answer: LastAnswerResult,
    val gate: InteractionGate,
    val resumeStepIndex: Int,
    val awaitingRating: Boolean,
    val reviewed: Boolean
)

internal fun SessionState.cardProgress() = CardProgress(
    revealedPages = revealedPages,
    answer = lastAnswerResult.takeUnless { status == SessionStatus.LISTENING } ?: LastAnswerResult(),
    gate = interactionGate,
    resumeStepIndex = pendingStepIndexToRun ?: stepIndex,
    awaitingRating = status == SessionStatus.AWAITING_RATING || cardReviewed,
    reviewed = cardReviewed
)

/** Odkłada bieżącą kartę i odtwarza odwiedzoną; pierwsza wizyta zaczyna się od pytania. */
internal fun SessionState.visitCard(index: Int): SessionState {
    if (index !in cards.indices || index == currentCardIndex) return this
    val source = cards.getOrNull(currentCardIndex) ?: return this
    val history = cardHistory + (source.id to cardProgress())
    val previous = history[cards[index].id]
    return copy(
        currentCardIndex = index,
        cardHistory = history,
        status = if (previous?.awaitingRating == true) SessionStatus.AWAITING_RATING else SessionStatus.IDLE,
        revealedPages = previous?.revealedPages ?: listOf(0),
        lastAnswerResult = previous?.answer ?: LastAnswerResult(),
        previousAnswerResult = LastAnswerResult(),
        interactionGate = previous?.gate ?: InteractionGate(),
        pendingStepIndexToRun = null,
        stepIndex = previous?.resumeStepIndex ?: 0,
        cardReviewed = previous?.reviewed == true,
        revisiting = previous != null,
        peekedPageIndex = null,
        audioPageIndex = null,
        error = null,
        navigation = null
    )
}
