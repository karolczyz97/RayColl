import { useCallback, useRef, useEffect, useMemo, useReducer } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '@/types/models';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from '@/features/study/session/sessionReducer';
import { useSyncedRef } from './useSyncedRef';
import type { SessionAction } from '@/features/study/session/sessionTypes';
import { executeStudyStep } from '@/features/study/session/stepExecutor';
import { areAllActivePagesRevealed } from '@/features/study/session/sessionUtils';
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
  // Generation counter for step chains; see runEpochRef in stepExecutor.ts.
  const runEpochRef = useRef(0);
  const pausedRef = useRef(false);
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
    lastPartialTextRef,
  } = useStudyAudio(dispatchIfMounted, ttsRateRef);

  const prepareStartSession = useCallback(() => {
    abortRef.current = false;
    pausedRef.current = false;
    runEpochRef.current += 1;
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
        runEpochRef,
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
        lastPartialTextRef,
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
      lastPartialTextRef,
      lastTtsDurationRef,
      markCardFailed,
      playTts,
      processCardReview,
      runSpeechRecognition,
      skipRef,
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

  // Auto-advance reveal_on_tap when all pages are already visible.
  // Handles the case where pages were pre-revealed during an audio-step skip tap
  // (setHolding reveals pages even outside waitingForTap). Without this, the user
  // would need an extra tap after the skip reveals the last page.
  // Guard in the setTimeout checks stateRef so if handleCardPress already advanced,
  // this becomes a no-op.
  useEffect(() => {
    if (!state.waitingForTap) return;
    if (!group) return;
    if (!areAllActivePagesRevealed(group, state.revealedPages)) return;
    const card = dueCards[state.currentCardIndex];
    if (!card) return;

    const stepIndex = state.currentStepIndex;
    const cardIndex = state.currentCardIndex;

    const timer = setTimeout(() => {
      if (
        stateRef.current.waitingForTap &&
        stateRef.current.currentStepIndex === stepIndex &&
        stateRef.current.currentCardIndex === cardIndex &&
        !abortRef.current
      ) {
        dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex, waitingForTap: false });
        void executeStepRef.current?.(card, stepIndex + 1);
      }
    }, 0);

    return () => clearTimeout(timer);
  }, [
    state.waitingForTap,
    state.revealedPages,
    state.currentStepIndex,
    state.currentCardIndex,
    group,
    dueCards,
    dispatchIfMounted,
    stateRef,
    abortRef,
  ]);

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

  // End the run early (e.g. user confirmed exit): stop any audio/STT and mark the
  // session finished so the summary screen renders in place instead of navigating.
  const endSession = useCallback(() => {
    pausedRef.current = false;
    stopSession();
    dispatchIfMounted({ type: 'FINISH_SESSION' });
  }, [dispatchIfMounted, stopSession]);

  // Freeze the run while a modal (exit confirm) is up: kill the in-flight step
  // chain and stop TTS/STT. No-op when nothing runs in the background (waiting
  // for a tap or a manual rating, or the session is finished).
  const pauseSession = useCallback(() => {
    const current = stateRef.current;
    if (current.status === 'finished' || current.status === 'revealed' || current.waitingForTap) {
      return;
    }
    pausedRef.current = true;
    runEpochRef.current += 1;
    abortRef.current = true;
    stopAudio();
  }, [stateRef, stopAudio]);

  // Resume after a pause by replaying the current card from its first step —
  // TTS/STT cannot resume mid-utterance, so a clean replay is the honest option.
  const resumeSession = useCallback(() => {
    if (!pausedRef.current) return;
    pausedRef.current = false;
    abortRef.current = false;
    runEpochRef.current += 1;
    const cardIndex = stateRef.current.currentCardIndex;
    const card = dueCardsRef.current[cardIndex];
    dispatchIfMounted({ type: 'ADVANCE_CARD', nextCardIndex: cardIndex });
    if (!card) return;
    // Kick the replay explicitly: the auto-execute effect may not re-fire when the
    // paused state was already idle (e.g. paused during a wait step).
    lastExecutedCardIndexRef.current = cardIndex;
    setTimeout(() => {
      if (!abortRef.current) {
        void executeStepRef.current?.(card, 0);
      }
    }, 0);
  }, [dispatchIfMounted, dueCardsRef, stateRef]);

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
      audioPageIndex: state.audioPageIndex,
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
    endSession,
    pauseSession,
    resumeSession,
    setHolding,
    restartSession,
    restartFailed,
    failedCount,
    clearError,
  };
}
