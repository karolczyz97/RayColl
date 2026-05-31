import type { Flashcard } from '../../../types/models';

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
  peekedPages: number[];
  sttResultText: string;
  sttMatchPercent: number;
  waitingForTap: boolean;
  errorMsg?: string;
}

export type SessionAction =
  | { type: 'START_SESSION'; cards: Flashcard[] }
  | {
      type: 'SET_CURRENT_STEP';
      stepIndex: number;
      revealedPages?: number[];
      waitingForTap?: boolean;
    }
  | { type: 'START_SPEAKING'; stepIndex: number }
  | { type: 'END_SPEAKING' }
  | { type: 'START_LISTENING'; stepIndex: number }
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
