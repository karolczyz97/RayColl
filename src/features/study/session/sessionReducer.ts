import type { StudySessionState, SessionAction } from './sessionTypes';
import { NO_ANSWER_RESULT, NO_INTERACTION_GATE } from './sessionTypes';
import { uniquePageIndexes } from './sessionUtils';

export const INITIAL_STUDY_SESSION_STATE: StudySessionState = {
  status: 'idle',
  currentCardIndex: 0,
  currentStepIndex: 0,
  revealedPages: [],
  peekedPageIndex: null,
  lastAnswerResult: NO_ANSWER_RESULT,
  audioPageIndex: null,
  interactionGate: NO_INTERACTION_GATE,
  pendingStepIndexToRun: null,
  errorMsg: undefined,
};

// Dla REVEAL_*: jeśli aktualnie podglądana (peek) strona właśnie się odsłoniła,
// peek znika, bo jest już widoczna na stałe.
function clearPeekIfRevealed(
  peekedPageIndex: number | null,
  revealedPages: number[],
): number | null {
  return peekedPageIndex != null && revealedPages.includes(peekedPageIndex)
    ? null
    : peekedPageIndex;
}

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
        lastAnswerResult: NO_ANSWER_RESULT,
      };
    case 'UPDATE_PARTIAL_STT':
      // Ignore late partial results that arrive after we've left the listening
      // state (e.g. a zombie web-speech callback firing post-skip/advance).
      if (state.status !== 'listening') return state;
      return {
        ...state,
        lastAnswerResult: { ...state.lastAnswerResult, text: action.text },
      };
    case 'SET_LAST_ANSWER_RESULT':
      return {
        ...state,
        status: 'idle',
        audioPageIndex: null,
        lastAnswerResult: action.result,
      };
    case 'REVEAL_PAGES': {
      const newRevealed = uniquePageIndexes(action.revealedPages);
      return {
        ...state,
        revealedPages: newRevealed,
        peekedPageIndex: clearPeekIfRevealed(state.peekedPageIndex, newRevealed),
      };
    }
    case 'REVEAL_PAGE': {
      const newRevealed = uniquePageIndexes([...state.revealedPages, action.pageIndex]);
      return {
        ...state,
        currentStepIndex: action.stepIndex,
        revealedPages: newRevealed,
        peekedPageIndex: clearPeekIfRevealed(state.peekedPageIndex, newRevealed),
      };
    }
    case 'REVEAL_NEXT_PAGE_IN_GATE': {
      const newRevealed = uniquePageIndexes([...state.revealedPages, action.pageIndex]);
      return {
        ...state,
        revealedPages: newRevealed,
        peekedPageIndex: clearPeekIfRevealed(state.peekedPageIndex, newRevealed),
      };
    }
    case 'SHOW_RATINGS':
      return {
        ...state,
        status: 'revealed',
        peekedPageIndex: null,
      };
    case 'FINISH_SESSION':
      return {
        ...state,
        status: 'finished',
        peekedPageIndex: null,
        interactionGate: NO_INTERACTION_GATE,
        pendingStepIndexToRun: null,
      };
    case 'ADVANCE_CARD':
      return {
        ...INITIAL_STUDY_SESSION_STATE,
        currentCardIndex: action.nextCardIndex,
      };
    case 'START_TAP_REVEAL_GATE':
      return {
        ...state,
        status: 'idle',
        interactionGate: {
          kind: 'tap_to_reveal',
          revealMode: action.revealMode,
          continueStepIndex: action.continueStepIndex,
        },
      };
    case 'COMPLETE_INTERACTION_GATE':
      return {
        ...state,
        interactionGate: NO_INTERACTION_GATE,
      };
    case 'SET_PENDING_STEP_INDEX':
      return {
        ...state,
        pendingStepIndexToRun: action.stepIndex,
      };
    case 'CONSUME_PENDING_STEP_INDEX':
      return {
        ...state,
        pendingStepIndexToRun: null,
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
