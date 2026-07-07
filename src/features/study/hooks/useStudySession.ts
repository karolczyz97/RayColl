import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import type { FlashcardGroup, Flashcard, AtomicStep } from '@/types/models';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { SessionEngine } from '@/features/study/session/SessionEngine';
import { useSyncedRef } from '@/hooks/useSyncedRef';
import { useStudyAudio } from '@/features/study/hooks/useStudyAudio';
import { useStudyCardGestures } from '@/features/study/hooks/useStudyCardGestures';

/**
 * Cienki adapter React nad SessionEngine: subskrybuje stan silnika, wstrzykuje
 * zależności audio i mapuje stan na view-model dla UI. Cała logika przebiegu
 * sesji (łańcuchy kroków, oceny, pauza/wznowienie) żyje w silniku.
 */
export function useStudySession(
  group: FlashcardGroup | null,
  steps: AtomicStep[],
  onCardReviewed: (groupId: string, cardId: string, rating: number) => void,
) {
  const [engine] = useState(() => new SessionEngine());
  const state = useSyncExternalStore(engine.subscribe, engine.getState, engine.getState);
  const dueCards = useSyncExternalStore(engine.subscribe, engine.getDueCards, engine.getDueCards);
  const failedCount = useSyncExternalStore(
    engine.subscribe,
    engine.getFailedCount,
    engine.getFailedCount,
  );

  const { ttsRate } = useAppTheme();
  const ttsRateRef = useSyncedRef(ttsRate);
  const onCardReviewedRef = useSyncedRef(onCardReviewed);

  const {
    playTts,
    runSpeechRecognition,
    requestSkip,
    guardedAwait,
    stopAudio,
    lastTtsDurationRef,
    skipRef,
  } = useStudyAudio(engine.dispatch, ttsRateRef);

  const activePageCount = group?.activePageCount ?? Infinity;
  const activeSteps = useMemo(
    () =>
      steps.filter((step) => {
        if ('pageIndex' in step && step.pageIndex >= activePageCount) return false;
        if ('nextPageIndex' in step && step.nextPageIndex >= activePageCount) return false;
        return true;
      }),
    [activePageCount, steps],
  );

  // Konfiguracja i dane wejściowe silnika po każdym renderze — parytet z
  // dawnymi useSyncedRef, które również aktualizowały się w efekcie.
  useEffect(() => {
    engine.configure({
      playTts,
      runSpeechRecognition,
      guardedAwait,
      stopAudio,
      skip: skipRef.current,
      getLastTtsDuration: () => lastTtsDurationRef.current,
      onCardReviewed: (groupId: string, cardId: string, rating: number) =>
        onCardReviewedRef.current(groupId, cardId, rating),
    });
    engine.setGroup(group);
    engine.setActiveSteps(activeSteps);
  });

  const { handleCardPress, setHolding } = useStudyCardGestures({
    engine,
    skipRef,
    requestSkip,
  });

  useEffect(() => {
    return () => {
      engine.markUnmounted();
      engine.stop();
    };
  }, [engine]);

  const handleRating = useCallback(
    async (rating: number) => {
      await engine.rate(rating);
    },
    [engine],
  );

  const clearError = useCallback(() => {
    engine.dispatch({ type: 'CLEAR_ERROR' });
  }, [engine]);

  const startSession = useCallback(
    (cards: Flashcard[]) => {
      engine.start(cards);
    },
    [engine],
  );

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
    stopSession: engine.stop,
    endSession: engine.end,
    pauseSession: engine.pause,
    resumeSession: engine.resume,
    setHolding,
    restartSession: engine.restart,
    restartFailed: engine.restartFailed,
    failedCount,
    clearError,
  };
}
