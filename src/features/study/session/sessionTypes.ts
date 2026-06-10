export type SessionStatus =
  | 'idle'
  | 'speaking'
  | 'listening'
  | 'checking'
  | 'revealed'
  | 'finished'
  | 'error';

export interface StudySessionState {
  status: SessionStatus;
  currentCardIndex: number;
  currentStepIndex: number;
  revealedPages: number[];
  peekedPageIndex: number | null;
  sttResultText: string;
  sttMatchPercent: number;
  waitingForTap: boolean;
  // Page currently being spoken (TTS) or listened to (STT), so the card can show
  // a per-page audio indicator. Null when no audio step is active.
  audioPageIndex: number | null;
  errorMsg?: string;
}

export type SessionAction =
  | { type: 'START_SESSION' }
  | {
      type: 'SET_CURRENT_STEP';
      stepIndex: number;
      revealedPages?: number[];
      waitingForTap?: boolean;
    }
  | { type: 'START_SPEAKING'; stepIndex: number; pageIndex: number }
  | { type: 'END_SPEAKING' }
  | { type: 'START_LISTENING'; stepIndex: number; pageIndex: number }
  | { type: 'UPDATE_PARTIAL_STT'; text: string }
  | { type: 'END_LISTENING'; text: string; matchPercent: number }
  | { type: 'REVEAL_PAGES'; revealedPages: number[]; waitingForTap?: boolean }
  | { type: 'REVEAL_PAGE'; stepIndex: number; pageIndex: number }
  | { type: 'SHOW_RATINGS' }
  | { type: 'FINISH_SESSION' }
  | { type: 'ADVANCE_CARD'; nextCardIndex: number }
  | { type: 'SET_ERROR'; errorMsg: string }
  | { type: 'CLEAR_ERROR' }
  | { type: 'PEEK_SET'; pageIndex: number }
  | { type: 'PEEK_CLEAR' };
