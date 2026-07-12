package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.AtomicStep

enum class SessionStatus { Idle, Speaking, Listening, Revealed, Finished, Error }
enum class AnswerStatus { None, Correct, Incorrect, Skipped, Error }
enum class TapRevealMode { Single, Remaining, None }
enum class CardReviewState { None, AutoRated, ManuallyRated }

data class LastAnswerResult(
    val status: AnswerStatus = AnswerStatus.None,
    val text: String = "",
    val percent: Int? = null,
    val threshold: Int? = null,
    val pageIndex: Int? = null,
    val suggestedRating: Int? = null,
)

data class InteractionGate(
    val kind: String = "none",
    val revealMode: TapRevealMode? = null,
    val continueStepIndex: Int? = null,
)

data class StudySessionState(
    val status: SessionStatus = SessionStatus.Idle,
    val currentCardIndex: Int = 0,
    val currentStepIndex: Int = 0,
    val revealedPages: Set<Int> = setOf(0),
    val peekedPageIndex: Int? = null,
    val lastAnswerResult: LastAnswerResult = LastAnswerResult(),
    val audioPageIndex: Int? = null,
    val interactionGate: InteractionGate = InteractionGate(),
    val pendingStepIndexToRun: Int? = null,
    val errorMsg: String? = null,
    val cardReviewState: CardReviewState = CardReviewState.None,
    val isHolding: Boolean = false,
    val failedCardIds: Set<String> = emptySet(),
)

sealed interface SessionAction {
    data object StartSession : SessionAction
    data class SetCurrentStep(val stepIndex: Int) : SessionAction
    data class StartSpeaking(val stepIndex: Int, val pageIndex: Int) : SessionAction
    data object EndSpeaking : SessionAction
    data class StartListening(val stepIndex: Int, val pageIndex: Int) : SessionAction
    data class UpdatePartialStt(val text: String) : SessionAction
    data class SetLastAnswerResult(val result: LastAnswerResult) : SessionAction
    data class RevealPages(val pages: Set<Int>) : SessionAction
    data class RevealPage(val stepIndex: Int, val pageIndex: Int) : SessionAction
    data class RevealNextPageInGate(val pageIndex: Int) : SessionAction
    data object ShowRatings : SessionAction
    data object FinishSession : SessionAction
    data class AdvanceCard(val nextCardIndex: Int) : SessionAction
    data class SetError(val errorMsg: String) : SessionAction
    data object ClearError : SessionAction
    data class PeekSet(val pageIndex: Int) : SessionAction
    data object PeekClear : SessionAction
    data class StartTapRevealGate(val continueStepIndex: Int, val revealMode: TapRevealMode) : SessionAction
    data object CompleteInteractionGate : SessionAction
    data class SetPendingStepIndex(val stepIndex: Int) : SessionAction
    data object ConsumePendingStepIndex : SessionAction
    data class MarkFailed(val cardId: String) : SessionAction
    data class SetHolding(val holding: Boolean) : SessionAction
}

fun sessionReducer(state: StudySessionState, action: SessionAction): StudySessionState = when (action) {
    is SessionAction.StartSession -> state.copy(status = SessionStatus.Idle, currentCardIndex = 0, currentStepIndex = 0, revealedPages = setOf(0))
    is SessionAction.SetCurrentStep -> state.copy(currentStepIndex = action.stepIndex)
    is SessionAction.StartSpeaking -> state.copy(status = SessionStatus.Speaking, currentStepIndex = action.stepIndex, audioPageIndex = action.pageIndex)
    is SessionAction.EndSpeaking -> state.copy(status = SessionStatus.Idle, audioPageIndex = null)
    is SessionAction.StartListening -> state.copy(status = SessionStatus.Listening, currentStepIndex = action.stepIndex, audioPageIndex = action.pageIndex)
    is SessionAction.UpdatePartialStt -> if (state.status == SessionStatus.Listening) state.copy(lastAnswerResult = state.lastAnswerResult.copy(text = action.text)) else state
    is SessionAction.SetLastAnswerResult -> state.copy(lastAnswerResult = action.result, audioPageIndex = null)
    is SessionAction.RevealPages -> state.copy(revealedPages = action.pages, peekedPageIndex = null)
    is SessionAction.RevealPage -> state.copy(revealedPages = state.revealedPages + action.pageIndex, currentStepIndex = action.stepIndex, peekedPageIndex = null)
    is SessionAction.RevealNextPageInGate -> state.copy(revealedPages = state.revealedPages + action.pageIndex)
    is SessionAction.ShowRatings -> state.copy(status = SessionStatus.Revealed)
    is SessionAction.FinishSession -> state.copy(status = SessionStatus.Finished)
    is SessionAction.AdvanceCard -> state.copy(status = SessionStatus.Idle, currentCardIndex = action.nextCardIndex, currentStepIndex = 0, revealedPages = setOf(0), peekedPageIndex = null, interactionGate = InteractionGate(), lastAnswerResult = LastAnswerResult(), cardReviewState = CardReviewState.None)
    is SessionAction.SetError -> state.copy(status = SessionStatus.Error, errorMsg = action.errorMsg)
    is SessionAction.ClearError -> state.copy(errorMsg = null)
    is SessionAction.PeekSet -> state.copy(peekedPageIndex = action.pageIndex)
    is SessionAction.PeekClear -> state.copy(peekedPageIndex = null)
    is SessionAction.StartTapRevealGate -> state.copy(interactionGate = InteractionGate(kind = "tap_to_reveal", revealMode = action.revealMode, continueStepIndex = action.continueStepIndex))
    is SessionAction.CompleteInteractionGate -> state.copy(interactionGate = InteractionGate(), pendingStepIndexToRun = state.interactionGate.continueStepIndex)
    is SessionAction.SetPendingStepIndex -> state.copy(pendingStepIndexToRun = action.stepIndex)
    is SessionAction.ConsumePendingStepIndex -> state.copy(pendingStepIndexToRun = null)
    is SessionAction.MarkFailed -> state.copy(failedCardIds = state.failedCardIds + action.cardId)
    is SessionAction.SetHolding -> state.copy(isHolding = action.holding)
}
