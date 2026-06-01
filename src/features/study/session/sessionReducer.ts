import type { StudySessionState, SessionAction } from './sessionTypes';
import { uniquePageIndexes } from './sessionUtils';

export const INITIAL_STUDY_SESSION_STATE: StudySessionState = {
  status: 'idle',
  currentCardIndex: 0,
  currentStepIndex: 0,
  revealedPages: [],
  peekedPages: [],
  sttResultText: '',
  sttMatchPercent: 0,
  waitingForTap: false,
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
        peekedPages: [],
      };
    case 'START_SPEAKING':
      return {
        ...state,
        status: 'speaking',
        currentStepIndex: action.stepIndex,
      };
    case 'END_SPEAKING':
      return {
        ...state,
        status: 'idle',
      };
    case 'START_LISTENING':
      return {
        ...state,
        status: 'listening',
        currentStepIndex: action.stepIndex,
        sttResultText: '',
        sttMatchPercent: 0,
      };
    case 'UPDATE_PARTIAL_STT':
      return {
        ...state,
        sttResultText: action.text,
      };
    case 'END_LISTENING':
      return {
        ...state,
        status: 'checking',
        sttResultText: action.text,
        sttMatchPercent: action.matchPercent,
      };
    case 'REVEAL_PAGES': {
      const newRevealed = action.revealedPages;
      return {
        ...state,
        revealedPages: newRevealed,
        peekedPages: state.peekedPages.filter((p) => !newRevealed.includes(p)),
        waitingForTap: action.waitingForTap ?? state.waitingForTap,
      };
    }
    case 'REVEAL_PAGE': {
      const newRevealed = uniquePageIndexes([...state.revealedPages, action.pageIndex]);
      return {
        ...state,
        currentStepIndex: action.stepIndex,
        revealedPages: newRevealed,
        peekedPages: state.peekedPages.filter((p) => !newRevealed.includes(p)),
      };
    }
    case 'SHOW_RATINGS':
      return {
        ...state,
        status: 'revealed',
        waitingForTap: false,
        peekedPages: [],
      };
    case 'FINISH_SESSION':
      return {
        ...state,
        status: 'finished',
        waitingForTap: false,
        peekedPages: [],
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
        peekedPages: [action.pageIndex],
      };
    case 'PEEK_CLEAR':
      return {
        ...state,
        peekedPages: [],
      };
    case 'CLEAR_ERROR':
      // Only clear the message. Status must be preserved: by the time the user
      // dismisses the error, the step flow has already moved status forward
      // (e.g. to 'revealed'), and forcing 'idle' here would hide active UI.
      return {
        ...state,
        errorMsg: undefined,
      };
    default:
      return state;
  }
}
