package com.raycoll.app.domain.session

import com.raycoll.app.domain.model.Flashcard

enum class SessionStatus { IDLE, SPEAKING, LISTENING, REVEALED, FINISHED, ERROR }
enum class AnswerStatus { NONE, CORRECT, INCORRECT, SKIPPED, ERROR }

/** Błąd mowy pokazywany użytkownikowi (sesja idzie dalej). */
enum class SessionError { TTS, TTS_MISSING_VOICE, STT }

data class LastAnswerResult(
    val status: AnswerStatus = AnswerStatus.NONE,
    val text: String = "",
    val percent: Int? = null,
    val threshold: Int? = null,
    val pageIndex: Int? = null,
    val suggestedRating: Int? = null
)

enum class InteractionGateKind { NONE, TAP_TO_REVEAL }
enum class TapRevealMode { SINGLE, REMAINING, NONE }

data class InteractionGate(
    val kind: InteractionGateKind = InteractionGateKind.NONE,
    val revealMode: TapRevealMode = TapRevealMode.NONE,
    val continueStepIndex: Int? = null
)

data class SessionState(
    val status: SessionStatus = SessionStatus.IDLE,
    val currentCardIndex: Int = 0,
    val revealedPages: List<Int> = listOf(0),
    val peekedPageIndex: Int? = null,
    /** Wynik odpowiedzi bieżącej karty — czytają go silnik i warunki kroków (CORRECT/WRONG). */
    val lastAnswerResult: LastAnswerResult = LastAnswerResult(),
    /**
     * Wynik poprzedniej karty — wyłącznie dla animacji znikania panelu odpowiedzi po przejściu
     * do następnej karty. Silnik i warunki kroków go nie czytają.
     */
    val previousAnswerResult: LastAnswerResult = LastAnswerResult(),
    val audioPageIndex: Int? = null,
    val interactionGate: InteractionGate = InteractionGate(),
    val pendingStepIndexToRun: Int? = null,
    val error: SessionError? = null,
    val cards: List<Flashcard> = emptyList(),
    val failedCardIds: Set<String> = emptySet(),
    val cardReviewed: Boolean = false
)

sealed interface SessionAction {
    data class StartSession(val cards: List<Flashcard>) : SessionAction
    data class StartSpeaking(val pageIndex: Int) : SessionAction
    data object EndSpeaking : SessionAction
    data class StartListening(val pageIndex: Int) : SessionAction
    data class UpdatePartialStt(val text: String) : SessionAction
    data class SetLastAnswerResult(val result: LastAnswerResult) : SessionAction
    data class RevealPages(val revealedPages: List<Int>) : SessionAction
    data object ShowRatings : SessionAction
    data object FinishSession : SessionAction
    data class AdvanceCard(val nextCardIndex: Int) : SessionAction
    data class SetError(val error: SessionError) : SessionAction
    data object ClearError : SessionAction
    data class PeekSet(val pageIndex: Int) : SessionAction
    data object PeekClear : SessionAction
    data class StartTapRevealGate(val continueStepIndex: Int, val revealMode: TapRevealMode) : SessionAction
    data object CompleteInteractionGate : SessionAction
    data object ConsumePendingStepIndex : SessionAction
    data class AddFailedCard(val cardId: String) : SessionAction
    data class RemoveFailedCard(val cardId: String) : SessionAction
    data object SetCardReviewed : SessionAction
}
