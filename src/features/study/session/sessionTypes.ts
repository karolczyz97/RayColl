export type SessionStatus =
  | 'idle'
  | 'speaking'
  | 'listening'
  | 'revealed'
  | 'finished'
  | 'error';

/** Wynik ostatniego "Sprawdź wymowę" (STT) na bieżącej karcie. */
export type AnswerStatus = 'none' | 'correct' | 'incorrect' | 'skipped' | 'error';

export interface LastAnswerResult {
  status: AnswerStatus;
  text: string;
  percent: number | null;
  threshold: number | null;
  pageIndex: number | null;
  // Ocena SRS sugerowana z wyniku (percent+threshold); używana TYLKO przez
  // krok auto_rate_from_answer. STT samo niczego nie ocenia.
  suggestedRating: number | null;
}

export const NO_ANSWER_RESULT: LastAnswerResult = {
  status: 'none',
  text: '',
  percent: null,
  threshold: null,
  pageIndex: null,
  suggestedRating: null,
};

export type InteractionGateKind = 'none' | 'tap_to_reveal';
// 'none' = prosty gate (wait_for_tap): tap niczego nie odsłania, tylko domyka gate.
export type TapRevealMode = 'single' | 'remaining' | 'none';

/**
 * Gdy runner dochodzi do kroku `wait_for_tap_to_reveal`, zatrzymuje się i otwiera
 * ten gate. Każdy tap odsłania jedną kolejną aktywną ukrytą stronę; ostatni tap
 * domyka gate i ustawia `pendingStepIndexToRun = continueStepIndex`, żeby runner
 * wznowił od następnego kroku. To NIE jest to samo co hold-to-advance (holdingRef).
 */
export interface InteractionGate {
  kind: InteractionGateKind;
  revealMode: TapRevealMode | null;
  // Krok, od którego runner wznawia po domknięciu gate (zwykle stepIndex + 1).
  continueStepIndex: number | null;
}

export const NO_INTERACTION_GATE: InteractionGate = {
  kind: 'none',
  revealMode: null,
  continueStepIndex: null,
};

/**
 * Stan oceny bieżącej karty. Jedna karta = maksymalnie jedna ocena: gdy stan
 * jest inny niż `none`, kolejne auto/manual oceny i `show_ratings` są no-op.
 * Trzymany jako ref w useStudySession (runner czyta go synchronicznie w łańcuchu
 * kroków; dispatch reducera byłby asynchroniczny i nieczytelny w tym samym ticku).
 */
export type CardReviewState = 'none' | 'autoRated' | 'manuallyRated';

export interface StudySessionState {
  status: SessionStatus;
  currentCardIndex: number;
  currentStepIndex: number;
  revealedPages: number[];
  peekedPageIndex: number | null;
  // Wynik ostatniego STT — mirror dla UI. Synchronicznym źródłem prawdy dla
  // runnera/warunków jest lastAnswerResultRef w useStudySession.
  lastAnswerResult: LastAnswerResult;
  // Page currently being spoken (TTS) or listened to (STT), so the card can show
  // a per-page audio indicator. Null when no audio step is active.
  audioPageIndex: number | null;
  interactionGate: InteractionGate;
  // Ustawiane przez gate / gesty; efekt w useStudySession podejmuje od tego kroku
  // na bieżącej karcie (omija blokadę lastExecutedCardIndexRef auto-runa).
  pendingStepIndexToRun: number | null;
  errorMsg?: string;
}

export type SessionAction =
  | { type: 'START_SESSION' }
  | { type: 'SET_CURRENT_STEP'; stepIndex: number }
  | { type: 'START_SPEAKING'; stepIndex: number; pageIndex: number }
  | { type: 'END_SPEAKING' }
  | { type: 'START_LISTENING'; stepIndex: number; pageIndex: number }
  | { type: 'UPDATE_PARTIAL_STT'; text: string }
  | { type: 'SET_LAST_ANSWER_RESULT'; result: LastAnswerResult }
  | { type: 'REVEAL_PAGES'; revealedPages: number[] }
  | { type: 'REVEAL_PAGE'; stepIndex: number; pageIndex: number }
  | { type: 'REVEAL_NEXT_PAGE_IN_GATE'; pageIndex: number }
  | { type: 'SHOW_RATINGS' }
  | { type: 'FINISH_SESSION' }
  | { type: 'ADVANCE_CARD'; nextCardIndex: number }
  | { type: 'SET_ERROR'; errorMsg: string }
  | { type: 'CLEAR_ERROR' }
  | { type: 'PEEK_SET'; pageIndex: number }
  | { type: 'PEEK_CLEAR' }
  | { type: 'START_TAP_REVEAL_GATE'; continueStepIndex: number; revealMode: TapRevealMode }
  | { type: 'COMPLETE_INTERACTION_GATE' }
  | { type: 'SET_PENDING_STEP_INDEX'; stepIndex: number }
  | { type: 'CONSUME_PENDING_STEP_INDEX' };
