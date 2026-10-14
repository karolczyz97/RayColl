package com.raycoll.app.domain.session

fun sessionReducer(state: SessionState, action: SessionAction): SessionState = when (action) {
    is SessionAction.StartSession -> state.copy(
        status = SessionStatus.IDLE,
        currentCardIndex = 0,
        revealedPages = listOf(0),
        peekedPageIndex = null,
        lastAnswerResult = LastAnswerResult(),
        previousAnswerResult = LastAnswerResult(),
        audioPageIndex = null,
        interactionGate = InteractionGate(),
        pendingStepIndexToRun = null,
        error = null,
        failedCardIds = emptySet(),
        cardReviewed = false
    )

    is SessionAction.StartSpeaking -> state.copy(
        status = SessionStatus.SPEAKING,
        audioPageIndex = action.pageIndex
    )

    is SessionAction.EndSpeaking -> state.copy(status = SessionStatus.IDLE, audioPageIndex = null)

    is SessionAction.StartListening -> state.copy(
        status = SessionStatus.LISTENING,
        audioPageIndex = action.pageIndex,
        lastAnswerResult = LastAnswerResult()
    )

    is SessionAction.UpdatePartialStt -> {
        if (state.status != SessionStatus.LISTENING) {
            state
        } else {
            state.copy(lastAnswerResult = state.lastAnswerResult.copy(text = action.text))
        }
    }

    is SessionAction.SetLastAnswerResult -> state.copy(lastAnswerResult = action.result)

    is SessionAction.RevealPages -> {
        val newRevealed = (state.revealedPages + action.revealedPages).distinct().sorted()
        val clearedPeek = if (state.peekedPageIndex != null &&
            state.peekedPageIndex in newRevealed
        ) {
            null
        } else {
            state.peekedPageIndex
        }
        state.copy(revealedPages = newRevealed, peekedPageIndex = clearedPeek)
    }

    is SessionAction.ShowRatings -> if (state.cardReviewed) {
        state
    } else {
        state.copy(
            status = SessionStatus.REVEALED,
            peekedPageIndex = null
        )
    }

    is SessionAction.FinishSession -> state.copy(
        status = SessionStatus.FINISHED,
        peekedPageIndex = null,
        interactionGate = InteractionGate(),
        pendingStepIndexToRun = null,
        lastAnswerResult = LastAnswerResult(),
        previousAnswerResult = LastAnswerResult()
    )

    // nowa karta zaczyna bez wyniku; stary zostaje tylko do animacji znikania panelu
    is SessionAction.AdvanceCard -> state.copy(
        status = SessionStatus.IDLE,
        currentCardIndex = action.nextCardIndex,
        lastAnswerResult = LastAnswerResult(),
        previousAnswerResult = state.lastAnswerResult,
        revealedPages = listOf(0),
        peekedPageIndex = null,
        audioPageIndex = null,
        interactionGate = InteractionGate(),
        pendingStepIndexToRun = null,
        error = null,
        cardReviewed = false
    )

    is SessionAction.SetError -> state.copy(status = SessionStatus.ERROR, error = action.error)

    is SessionAction.ClearError -> state.copy(error = null)

    is SessionAction.PeekSet -> state.copy(peekedPageIndex = action.pageIndex)

    is SessionAction.PeekClear -> state.copy(peekedPageIndex = null)

    is SessionAction.StartTapRevealGate -> state.copy(
        status = SessionStatus.IDLE,
        lastAnswerResult = LastAnswerResult(),
        interactionGate = InteractionGate(
            kind = InteractionGateKind.TAP_TO_REVEAL,
            revealMode = action.revealMode,
            continueStepIndex = action.continueStepIndex
        )
    )

    is SessionAction.CompleteInteractionGate -> {
        val continueIdx = state.interactionGate.continueStepIndex
        state.copy(
            interactionGate = InteractionGate(),
            pendingStepIndexToRun = continueIdx
        )
    }

    is SessionAction.ConsumePendingStepIndex -> state.copy(pendingStepIndexToRun = null)

    is SessionAction.SetCards -> state.copy(cards = action.cards)

    is SessionAction.AddFailedCard -> state.copy(failedCardIds = state.failedCardIds + action.cardId)

    is SessionAction.RemoveFailedCard -> state.copy(failedCardIds = state.failedCardIds - action.cardId)

    is SessionAction.SetCardReviewed -> state.copy(cardReviewed = true)
}
