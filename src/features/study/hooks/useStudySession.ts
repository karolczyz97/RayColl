import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import type { FlashcardGroup, Flashcard, AtomicStep } from '@/types/models';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { SessionEngine } from '@/features/study/session/SessionEngine';
import { buildSessionViewModel } from '@/features/study/session/sessionViewModel';
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

  const { runSpeechRecognition, requestSkip, guardedAwait, stopAudio, skipRef } = useStudyAudio(
    engine.dispatch,
  );

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
    engine.configure(
      {
        runSpeechRecognition,
        guardedAwait,
        stopAudio,
        skip: skipRef.current,
        onCardReviewed: (groupId: string, cardId: string, rating: number) =>
          onCardReviewedRef.current(groupId, cardId, rating),
      },
      ttsRateRef.current,
    );
    engine.setGroup(group);
    engine.setActiveSteps(activeSteps);
  });

  const { handleCardPress, setHolding } = useStudyCardGestures({
    engine,
    skipRef,
    requestSkip,
  });

  // Cleanup: Zawsze zatrzymuj silnik przy odmontowaniu widoku
  useEffect(() => {
    return () => {
      engine.stop();
      engine.markUnmounted();
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

  const sessionViewModel = useMemo(() => buildSessionViewModel(state), [state]);

  return {
    dueCards,
    sessionState: sessionViewModel,
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
    skipToNextCard: engine.skipToNextCard,
    goToPreviousCard: engine.goToPreviousCard,
    failedCount,
    clearError,
    engine,
  };
}
