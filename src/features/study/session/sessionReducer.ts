import type { StudySessionState, SessionAction } from './sessionTypes';
import { uniquePageIndexes } from './sessionUtils';

export const INITIAL_STUDY_SESSION_STATE: StudySessionState = {
  status: 'idle',
  currentCardIndex: 0,
  currentStepIndex: 0,
  revealedPages: [],
  peekedPageIndex: null,
  sttResultText: '',
  sttMatchPercent: 0,
  sttSuccessThreshold: null,
  sttPassed: null,
  waitingForTap: false,
  audioPageIndex: null,
  errorMsg: undefined,
};

export function sessionReducer(
  state: StudySessionState,
  action: SessionAction,
): StudySessionState {
  switch (action.type) {
    case 'START_SESSION':
      return {
        ...INITIAL_STUDY_SESSION_STATE,
      };
    case 'SET_CURRENT_STEP':
      return {
        ...state,
        currentStepIndex: action.stepIndex,
        revealedPages: action.revealedPages ?? state.revealedPages,
        waitingForTap: action.waitingForTap ?? state.waitingForTap,
        peekedPageIndex: null,
      };
    case 'START_SPEAKING':
      return {
        ...state,
        status: 'speaking',
        currentStepIndex: action.stepIndex,
        audioPageIndex: action.pageIndex,
      };
    case 'END_SPEAKING':
      return {
        ...state,
        status: 'idle',
        audioPageIndex: null,
      };
    case 'START_LISTENING':
      return {
        ...state,
        status: 'listening',
        currentStepIndex: action.stepIndex,
        audioPageIndex: action.pageIndex,
        sttResultText: '',
        sttMatchPercent: 0,
        sttSuccessThreshold: null,
        sttPassed: null,
      };
    case 'UPDATE_PARTIAL_STT':
      // Ignore late partial results that arrive after we've left the listening
      // state (e.g. a zombie web-speech callback firing post-skip/advance).
      if (state.status !== 'listening') return state;
      return {
        ...state,
        sttResultText: action.text,
      };
    case 'END_LISTENING':
      return {
        ...state,
        status: 'checking',
        audioPageIndex: null,
        sttResultText: action.text,
        sttMatchPercent: action.matchPercent,
        sttSuccessThreshold: action.successThreshold ?? null,
        sttPassed: action.passed ?? null,
      };
    case 'REVEAL_PAGES': {
      const newRevealed = uniquePageIndexes(action.revealedPages);
      return {
        ...state,
        revealedPages: newRevealed,
        peekedPageIndex:
          state.peekedPageIndex != null && newRevealed.includes(state.peekedPageIndex)
            ? null
            : state.peekedPageIndex,
        waitingForTap: action.waitingForTap ?? state.waitingForTap,
      };
    }
    case 'REVEAL_PAGE': {
      const newRevealed = uniquePageIndexes([...state.revealedPages, action.pageIndex]);
      return {
        ...state,
        currentStepIndex: action.stepIndex,
        revealedPages: newRevealed,
        peekedPageIndex:
          state.peekedPageIndex != null && newRevealed.includes(state.peekedPageIndex)
            ? null
            : state.peekedPageIndex,
      };
    }
    case 'SHOW_RATINGS':
      return {
        ...state,
        status: 'revealed',
        waitingForTap: false,
        peekedPageIndex: null,
      };
    case 'FINISH_SESSION':
      return {
        ...state,
        status: 'finished',
        waitingForTap: false,
        peekedPageIndex: null,
      };
    case 'ADVANCE_CARD':
      return {
        ...INITIAL_STUDY_SESSION_STATE,
        currentCardIndex: action.nextCardIndex,
      };
    case 'SET_ERROR':
      return {
        ...state,
        status: 'error',
        errorMsg: action.errorMsg,
      };
    case 'PEEK_SET':
      return {
        ...state,
        peekedPageIndex: action.pageIndex,
      };
    case 'PEEK_CLEAR':
      return {
        ...state,
        peekedPageIndex: null,
      };
    case 'CLEAR_ERROR':
      // Only clear the message. Status must be preserved: by the time the user
      // dismisses the error, the step flow has already moved status forward
      // (e.g. to 'revealed'), and forcing 'idle' here would hide active UI.
      return {
        ...state,
        errorMsg: undefined,
      };
    default: {
      // Compile-time exhaustiveness guard: a new SessionAction without a case
      // here fails typecheck instead of being silently ignored.
      const _exhaustive: never = action;
      void _exhaustive;
      return state;
    }
  }
}
