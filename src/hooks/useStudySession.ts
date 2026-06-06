import { useCallback, useRef, useEffect, useMemo, useReducer } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '@/types/models';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from '@/features/study/session/sessionReducer';
import { useSyncedRef } from './useSyncedRef';
import type { SessionAction } from '@/features/study/session/sessionTypes';
import { executeStudyStep } from '@/features/study/session/stepExecutor';
import { useStudyAudio } from '@/features/study/hooks/useStudyAudio';
import { useStudyCardGestures } from '@/features/study/hooks/useStudyCardGestures';
import { useStudyReviewFlow } from '@/features/study/hooks/useStudyReviewFlow';

export function useStudySession(
  group: FlashcardGroup | null,
  steps: ModeStep[],
  onCardReviewed: (groupId: string, cardId: string, rating: number) => void,
) {
  const [state, dispatch] = useReducer(sessionReducer, INITIAL_STUDY_SESSION_STATE);
  const { ttsRate } = useAppTheme();
  const abortRef = useRef(false);
  const isMountedRef = useRef(true);
  const holdingRef = useRef(false);
  const groupRef = useSyncedRef(group);
  const activeStepsRef = useRef<ModeStep[]>([]);
  const stateRef = useSyncedRef(state);
  const ttsRateRef = useSyncedRef(ttsRate);
  const onCardReviewedRef = useSyncedRef(onCardReviewed);
  const lastExecutedCardIndexRef = useRef<number | null>(null);
  const activePageCount = group?.activePageCount ?? Infinity;

  const dispatchIfMounted = useCallback((action: SessionAction) => {
    if (isMountedRef.current) {
      dispatch(action);
    }
  }, []);

  const {
    playTts,
    runSpeechRecognition,
    requestSkip,
    guardedAwait,
    stopAudio,
    lastTtsDurationRef,
    skipRef,
  } = useStudyAudio(dispatchIfMounted, ttsRateRef);

  const prepareStartSession = useCallback(() => {
    abortRef.current = false;
    lastExecutedCardIndexRef.current = null;
  }, []);

  const {
    dueCards,
    dueCardsRef,
    failedCount,
    startSession,
    processCardReview,
    advanceToNextCard,
    handleRating,
    restartSession,
    restartFailed,
    markCardFailed,
    unmarkCardFailed,
  } = useStudyReviewFlow({
    groupRef,
    stateRef,
    holdingRef,
    onCardReviewedRef,
    dispatchIfMounted,
    isMountedRef,
    prepareStartSession,
  });

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
      abortRef.current = true;
      stopAudio();
    };
  }, [stopAudio]);

  const activeSteps = useMemo(
    () =>
      steps.filter((step) => {
        if ('pageIndex' in step && step.pageIndex >= activePageCount) return false;
        if ('nextPageIndex' in step && step.nextPageIndex >= activePageCount) return false;
        return true;
      }),
    [activePageCount, steps],
  );

  useEffect(() => {
    activeStepsRef.current = activeSteps;
  }, [activeSteps]);

  const executeStepRef = useRef<(card: Flashcard, stepIndex: number) => Promise<void>>(undefined);

  const executeStep = useCallback(
    async (card: Flashcard, stepIndex: number) => {
      await executeStudyStep(card, stepIndex, {
        abortRef,
        activeStepsRef,
        groupRef,
        skipRef,
        lastTtsDurationRef,
        dispatchIfMounted,
        guardedAwait,
        playTts,
        runSpeechRecognition,
        processCardReview,
        advanceToNextCard,
        markCardFailed,
        unmarkCardFailed,
        executeNext: async (nextCard, nextStepIndex) => {
          await executeStepRef.current?.(nextCard, nextStepIndex);
        },
      });
    },
    [
      advanceToNextCard,
      dispatchIfMounted,
      groupRef,
      guardedAwait,
      lastTtsDurationRef,
      markCardFailed,
      playTts,
      processCardReview,
      runSpeechRecognition,
      skipRef,
      unmarkCardFailed,
    ],
  );

  useEffect(() => {
    executeStepRef.current = executeStep;
  }, [executeStep]);

  const resumeAfterStep = useCallback((card: Flashcard, nextStepIndex: number) => {
    // Defer so the reducer state (and stateRef) settle before the next step runs.
    setTimeout(() => {
      if (!abortRef.current) {
        void executeStepRef.current?.(card, nextStepIndex);
      }
    }, 0);
  }, []);

  const { handleCardPress, setHolding } = useStudyCardGestures({
    groupRef,
    stateRef,
    dueCardsRef,
    holdingRef,
    skipRef,
    requestSkip,
    resumeAfterStep,
    dispatchIfMounted,
  });

  useEffect(() => {
    if (dueCards.length === 0 || state.status === 'finished') {
      lastExecutedCardIndexRef.current = null;
      return;
    }
    if (state.status === 'revealed' || state.waitingForTap) return;
    const card = dueCards[state.currentCardIndex];
    if (!card) return;
    if (lastExecutedCardIndexRef.current === state.currentCardIndex) return;

    lastExecutedCardIndexRef.current = state.currentCardIndex;
    let active = true;
    const timer = setTimeout(() => {
      if (active) {
        void executeStep(card, 0);
      }
    }, 0);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [dueCards, executeStep, state.currentCardIndex, state.status, state.waitingForTap]);

  const stopSession = useCallback(() => {
    abortRef.current = true;
    stopAudio();
  }, [stopAudio]);

  const clearError = useCallback(() => {
    dispatchIfMounted({ type: 'CLEAR_ERROR' });
  }, [dispatchIfMounted]);

  const compatibilityState = useMemo(
    () => ({
      currentCardIndex: state.currentCardIndex,
      currentStepIndex: state.currentStepIndex,
      revealedPages: state.revealedPages,
      peekedPageIndex: state.peekedPageIndex,
      sttResultText: state.sttResultText,
      sttMatchPercent: state.sttMatchPercent,
      waitingForTap: state.waitingForTap,
      isTtsPlaying: state.status === 'speaking',
      isSttListening: state.status === 'listening',
      showRatingButtons: state.status === 'revealed',
      isSessionFinished: state.status === 'finished',
      status: state.status,
      errorMsg: state.errorMsg,
    }),
    [state],
  );

  return {
    dueCards,
    sessionState: compatibilityState,
    handleRating,
    handleCardPress,
    startSession,
    stopSession,
    setHolding,
    restartSession,
    restartFailed,
    failedCount,
    clearError,
  };
}
