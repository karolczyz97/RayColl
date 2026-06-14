import { useCallback, useRef, useEffect, useMemo, useReducer } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '@/types/models';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from '@/features/study/session/sessionReducer';
import { useSyncedRef } from './useSyncedRef';
import { executeStudyStep } from '@/features/study/session/stepExecutor';
import { useStudyAudio } from '@/features/study/hooks/useStudyAudio';
import { useStudyCardGestures } from '@/features/study/hooks/useStudyCardGestures';
import { useStudyReviewFlow } from '@/features/study/hooks/useStudyReviewFlow';
import {
  type SessionAction,
  type CardReviewState,
  type LastAnswerResult,
  NO_ANSWER_RESULT,
} from '@/features/study/session/sessionTypes';

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
  // Wynik ostatniego "Sprawdź wymowę" — synchroniczne źródło prawdy dla warunków
  // runnera (mirror dla UI leci przez reducer state.lastAnswerResult).
  const lastAnswerResultRef = useRef<LastAnswerResult>(NO_ANSWER_RESULT);
  // Stan oceny bieżącej karty: jedna karta = max jedna ocena (auto albo manual).
  const currentCardReviewStateRef = useRef<CardReviewState>('none');
  // Synchroniczny mirror revealedPages — reducer state jest stale w obrębie jednego
  // łańcucha kroków, a tap-gate/show_* muszą widzieć świeży zestaw odsłoniętych stron.
  const revealedPagesRef = useRef<number[]>([]);
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
    currentCardReviewStateRef,
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
        lastAnswerResultRef,
        currentCardReviewStateRef,
        revealedPagesRef,
        activeStepsRef,
        groupRef,
        stateRef,
        skipRef,
        lastTtsDurationRef,
        dispatchIfMounted,
        guardedAwait,
        playTts,
        runSpeechRecognition,
        processCardReview,
        advanceToNextCard,
        markCardFailed,
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
      stateRef,
    ],
  );

  useEffect(() => {
    executeStepRef.current = executeStep;
  }, [executeStep]);

  const { handleCardPress, setHolding } = useStudyCardGestures({
    groupRef,
    stateRef,
    holdingRef,
    skipRef,
    requestSkip,
    dispatchIfMounted,
  });

  // Po domknięciu tap-gate gesty ustawiają pendingStepIndexToRun. Wznawiamy runner
  // od wskazanego kroku TEJ SAMEJ karty — auto-run (poniżej) startuje kartę tylko
  // od kroku 0 i blokuje ponowne odpalenie przez lastExecutedCardIndexRef.
  useEffect(() => {
    const pending = state.pendingStepIndexToRun;
    if (pending === null) return;
    const card = dueCardsRef.current[state.currentCardIndex];
    // Konsumuj najpierw — re-render z pending=null wejdzie tu i wyjdzie od razu.
    dispatchIfMounted({ type: 'CONSUME_PENDING_STEP_INDEX' });
    if (!card || abortRef.current) return;
    // Synchronizuj mirror odsłoniętych stron ze świeżego state (zawiera reveale z gate).
    revealedPagesRef.current = stateRef.current.revealedPages;
    // Odpalamy bezpośrednio (bez setTimeout): CONSUME zmienia pending, więc cleanup
    // setTimeoutu anulowałby zaplanowane wznowienie zanim zdążyłoby wystartować.
    void executeStepRef.current?.(card, pending);
  }, [
    state.pendingStepIndexToRun,
    state.currentCardIndex,
    dueCardsRef,
    dispatchIfMounted,
    stateRef,
  ]);

  useEffect(() => {
    if (dueCards.length === 0 || state.status === 'finished') {
      lastExecutedCardIndexRef.current = null;
      return;
    }
    // Nie startuj karty od 0, gdy: pokazujemy ratingi, czekamy w tap-gate, albo
    // mamy zaplanowane wznowienie kroku (pendingStepIndexToRun) tej karty.
    if (state.status === 'revealed') return;
    if (state.interactionGate.kind !== 'none') return;
    if (state.pendingStepIndexToRun !== null) return;
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
  }, [
    dueCards,
    executeStep,
    state.currentCardIndex,
    state.status,
    state.interactionGate.kind,
    state.pendingStepIndexToRun,
  ]);

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
    if (
      current.status === 'finished' ||
      current.status === 'revealed' ||
      current.interactionGate.kind !== 'none'
    ) {
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

  const compatibilityState = useMemo(() => {
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
  }, [state]);

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
