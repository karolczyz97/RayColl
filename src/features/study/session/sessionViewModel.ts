import type { AnswerStatus, SessionStatus, StudySessionState } from './sessionTypes';

/**
 * Oficjalny view-model sesji dla UI (dawne `compatibilityState` w
 * useStudySession). Czysta projekcja stanu silnika na pola, którymi żywią się
 * ekrany nauki — bez logiki, bez efektów.
 */
export interface SessionViewModel {
  currentCardIndex: number;
  currentStepIndex: number;
  revealedPages: number[];
  peekedPageIndex: number | null;
  sttResultText: string;
  sttMatchPercent: number;
  sttSuccessThreshold: number | null;
  answerStatus: AnswerStatus;
  waitingForTap: boolean;
  audioPageIndex: number | null;
  isTtsPlaying: boolean;
  isSttListening: boolean;
  showRatingButtons: boolean;
  isSessionFinished: boolean;
  status: SessionStatus;
  errorMsg?: string;
}

export function buildSessionViewModel(state: StudySessionState): SessionViewModel {
  const answer = state.lastAnswerResult;
  return {
    currentCardIndex: state.currentCardIndex,
    currentStepIndex: state.currentStepIndex,
    revealedPages: state.revealedPages,
    peekedPageIndex: state.peekedPageIndex,
    // Pola STT wyprowadzone z kanonicznego lastAnswerResult (mirror dla UI).
    sttResultText: answer.text,
    sttMatchPercent: answer.percent ?? 0,
    sttSuccessThreshold: answer.threshold,
    answerStatus: answer.status,
    // Tap-gate napędza wskazówkę "tap to reveal" w UI (dawne waitingForTap).
    waitingForTap: state.interactionGate.kind === 'tap_to_reveal',
    audioPageIndex: state.audioPageIndex,
    isTtsPlaying: state.status === 'speaking',
    isSttListening: state.status === 'listening',
    showRatingButtons: state.status === 'revealed',
    isSessionFinished: state.status === 'finished',
    status: state.status,
    errorMsg: state.errorMsg,
  };
}
