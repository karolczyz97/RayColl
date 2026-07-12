package com.raycoll.app.domain.session

fun sessionReducer(state: SessionState, action: SessionAction): SessionState = when (action) {
    is SessionAction.StartSession -> state.copy(
        status = SessionStatus.IDLE,
        currentCardIndex = 0,
        currentStepIndex = 0,
        revealedPages = listOf(0),
        peekedPageIndex = null,
        lastAnswerResult = LastAnswerResult(),
        audioPageIndex = null,
        interactionGate = InteractionGate(),
        pendingStepIndexToRun = null,
        errorMsg = null,
        failedCardIds = emptySet(),
        cardReviewed = false,
    )
    is SessionAction.SetCurrentStep -> state.copy(currentStepIndex = action.stepIndex)
    is SessionAction.StartSpeaking -> state.copy(status = SessionStatus.SPEAKING, audioPageIndex = action.pageIndex, currentStepIndex = action.stepIndex)
    is SessionAction.EndSpeaking -> state.copy(status = SessionStatus.IDLE, audioPageIndex = null)
    is SessionAction.StartListening -> state.copy(status = SessionStatus.LISTENING, audioPageIndex = action.pageIndex, currentStepIndex = action.stepIndex)
    is SessionAction.UpdatePartialStt -> {
        if (state.status != SessionStatus.LISTENING) state
        else state.copy(lastAnswerResult = state.lastAnswerResult.copy(text = action.text))
    }
    is SessionAction.SetLastAnswerResult -> state.copy(lastAnswerResult = action.result)
    is SessionAction.RevealPages -> {
        val newRevealed = (state.revealedPages + action.revealedPages).distinct().sorted()
        val clearedPeek = if (state.peekedPageIndex != null && state.peekedPageIndex in newRevealed) null else state.peekedPageIndex
        state.copy(revealedPages = newRevealed, peekedPageIndex = clearedPeek)
    }
    is SessionAction.RevealPage -> {
        val newRevealed = (state.revealedPages + action.pageIndex).distinct().sorted()
        val clearedPeek = if (state.peekedPageIndex == action.pageIndex) null else state.peekedPageIndex
        state.copy(revealedPages = newRevealed, peekedPageIndex = clearedPeek, currentStepIndex = action.stepIndex)
    }
    is SessionAction.RevealNextPageInGate -> {
        val newRevealed = (state.revealedPages + action.pageIndex).distinct().sorted()
        val clearedPeek = if (state.peekedPageIndex == action.pageIndex) null else state.peekedPageIndex
        state.copy(revealedPages = newRevealed, peekedPageIndex = clearedPeek)
    }
    is SessionAction.ShowRatings -> if (state.cardReviewed) state else state.copy(status = SessionStatus.REVEALED)
    is SessionAction.FinishSession -> state.copy(status = SessionStatus.FINISHED)
    is SessionAction.AdvanceCard -> state.copy(
        currentCardIndex = action.nextCardIndex,
        currentStepIndex = 0,
        revealedPages = listOf(0),
        peekedPageIndex = null,
        lastAnswerResult = LastAnswerResult(),
        audioPageIndex = null,
        interactionGate = InteractionGate(),
        pendingStepIndexToRun = null,
        errorMsg = null,
        cardReviewed = false,
    )
    is SessionAction.SetError -> state.copy(status = SessionStatus.ERROR, errorMsg = action.errorMsg)
    is SessionAction.ClearError -> state.copy(errorMsg = null)
    is SessionAction.PeekSet -> state.copy(peekedPageIndex = action.pageIndex)
    is SessionAction.PeekClear -> state.copy(peekedPageIndex = null)
    is SessionAction.StartTapRevealGate -> state.copy(
        interactionGate = InteractionGate(
            kind = InteractionGateKind.TAP_TO_REVEAL,
            revealMode = action.revealMode,
            continueStepIndex = action.continueStepIndex,
        )
    )
    is SessionAction.CompleteInteractionGate -> {
        val continueIdx = state.interactionGate.continueStepIndex
        state.copy(
            interactionGate = InteractionGate(),
            pendingStepIndexToRun = continueIdx,
        )
    }
    is SessionAction.SetPendingStepIndex -> state.copy(pendingStepIndexToRun = action.stepIndex)
    is SessionAction.ConsumePendingStepIndex -> state.copy(pendingStepIndexToRun = null)
    is SessionAction.SetCards -> state.copy(cards = action.cards)
    is SessionAction.AddFailedCard -> state.copy(failedCardIds = state.failedCardIds + action.cardId)
    is SessionAction.RemoveFailedCard -> state.copy(failedCardIds = state.failedCardIds - action.cardId)
    is SessionAction.SetCardReviewed -> state.copy(cardReviewed = true)
}
