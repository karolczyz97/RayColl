import type { StudySessionState, SessionAction } from './sessionTypes';
import { uniquePageIndexes } from './sessionUtils';

export const INITIAL_STUDY_SESSION_STATE: StudySessionState = {
  status: 'idle',
  currentCardIndex: 0,
  currentStepIndex: 0,
  revealedPages: [],
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
    case 'REVEAL_PAGES':
      return {
        ...state,
        revealedPages: action.revealedPages,
        waitingForTap: action.waitingForTap ?? state.waitingForTap,
      };
    case 'REVEAL_PAGE':
      return {
        ...state,
        currentStepIndex: action.stepIndex,
        revealedPages: uniquePageIndexes([...state.revealedPages, action.pageIndex]),
      };
    case 'SHOW_RATINGS':
      return {
        ...state,
        status: 'revealed',
        waitingForTap: false,
      };
    case 'FINISH_SESSION':
      return {
        ...state,
        status: 'finished',
        waitingForTap: false,
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
    case 'CLEAR_ERROR':
      return {
        ...state,
        errorMsg: undefined,
        status: 'idle',
      };
    default:
      return state;
  }
}
