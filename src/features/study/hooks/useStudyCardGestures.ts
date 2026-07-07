import { useCallback, useEffect, useRef } from 'react';
import type { MutableRefObject } from 'react';
import type { StudySkipState } from '@/features/study/hooks/useStudyAudio';
import type { SessionEngine } from '@/features/study/session/SessionEngine';
import {
  areAllActivePagesRevealed,
  getNextHiddenPageIndex,
  PEEK_HOLD_THRESHOLD_MS,
  uniquePageIndexes,
} from '@/features/study/session/sessionUtils';
import {
  playCardPeekHaptic,
  playSelectionHaptic,
  playStudyActionHaptic,
} from '@/services/hapticFeedback';

interface UseStudyCardGesturesParams {
  engine: SessionEngine;
  skipRef: MutableRefObject<StudySkipState>;
  requestSkip: () => void;
}

interface GestureState {
  pressTime: number;
  hasPeeked: boolean;
  blockPress: boolean;
  peekTimer: ReturnType<typeof setTimeout> | null;
}

export function useStudyCardGestures({
  engine,
  skipRef,
  requestSkip,
}: UseStudyCardGesturesParams) {
  const gestureRef = useRef<GestureState>({
    pressTime: 0,
    hasPeeked: false,
    blockPress: false,
    peekTimer: null,
  });

  const clearPeekTimer = useCallback(() => {
    const gesture = gestureRef.current;
    if (gesture.peekTimer) {
      clearTimeout(gesture.peekTimer);
      gesture.peekTimer = null;
    }
  }, []);

  useEffect(() => () => clearPeekTimer(), [clearPeekTimer]);

  const handleCardPress = useCallback(() => {
    const gesture = gestureRef.current;
    // 1. Tap kończący long-press (peek) — pochłoń, nie rób nic więcej.
    if (gesture.blockPress) {
      gesture.blockPress = false;
      return;
    }
    const currentGroup = engine.getGroup();
    const currentState = engine.getState();
    if (!currentGroup) return;

    // 2. Karta oceniana / sesja zakończona — tap nic nie robi.
    if (currentState.status === 'revealed' || currentState.status === 'finished') return;

    // 3. Aktywny krok (TTS/STT/wait) uzbroił skip — tap TYLKO przerywa ten krok.
    //    Ten sam tap nie odsłania strony ani nie pokazuje ratingów.
    if (skipRef.current.armed) {
      playStudyActionHaptic();
      engine.dispatch({ type: 'PEEK_CLEAR' });
      requestSkip();
      return;
    }

    // 4. Tap-gate: każdy tap odsłania dokładnie jedną kolejną aktywną ukrytą stronę.
    if (currentState.interactionGate.kind === 'tap_to_reveal') {
      const continueStepIndex = currentState.interactionGate.continueStepIndex;
      const completeGate = () => {
        engine.dispatch({ type: 'COMPLETE_INTERACTION_GATE' });
        // Wznowienie runnera planuje pendingStepIndexToRun — NIE SHOW_RATINGS.
        // Ratingi pokaże dopiero krok show_ratings, jeśli jest następny.
        if (continueStepIndex !== null) {
          engine.dispatch({ type: 'SET_PENDING_STEP_INDEX', stepIndex: continueStepIndex });
        }
      };

      // Prosty gate (wait_for_tap): tap niczego nie odsłania, tylko domyka gate.
      if (currentState.interactionGate.revealMode === 'none') {
        playSelectionHaptic();
        completeGate();
        return;
      }

      const nextHiddenPageIndex = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);
      if (nextHiddenPageIndex === null) {
        // Defensywnie: gate nie powinien być otwarty bez ukrytych stron.
        completeGate();
        return;
      }

      const nextRevealedPages = uniquePageIndexes([
        ...currentState.revealedPages,
        nextHiddenPageIndex,
      ]);
      playSelectionHaptic();
      engine.dispatch({ type: 'REVEAL_NEXT_PAGE_IN_GATE', pageIndex: nextHiddenPageIndex });
      // Ostatni tap domyka gate (po odsłonięciu ostatniej ukrytej strony).
      if (
        currentState.interactionGate.revealMode === 'single' ||
        areAllActivePagesRevealed(currentGroup, nextRevealedPages)
      ) {
        completeGate();
      }
      return;
    }

    // 5. W innym przypadku tap nic nie robi.
  }, [engine, requestSkip, skipRef]);

  const setHolding = useCallback(
    (holding: boolean) => {
      engine.setHolding(holding);
      const gesture = gestureRef.current;
      const currentGroup = engine.getGroup();
      const currentState = engine.getState();

      if (holding) {
        clearPeekTimer();
        gesture.pressTime = Date.now();
        gesture.hasPeeked = false;
        gesture.blockPress = false;

        if (
          currentGroup &&
          currentState.status !== 'revealed' &&
          currentState.status !== 'finished'
        ) {
          const nextHidden = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);
          if (nextHidden !== null) {
            gesture.peekTimer = setTimeout(() => {
              gesture.hasPeeked = true;
              playCardPeekHaptic();
              // Peek NIGDY nie zmienia revealedPages — tylko podgląd jednej strony.
              engine.dispatch({ type: 'PEEK_SET', pageIndex: nextHidden });
            }, PEEK_HOLD_THRESHOLD_MS);
          }
        }
      } else {
        clearPeekTimer();
        const holdDuration = Date.now() - gesture.pressTime;

        if (holdDuration < PEEK_HOLD_THRESHOLD_MS) {
          gesture.blockPress = false;
        } else {
          gesture.blockPress = true;
          if (gesture.hasPeeked) {
            engine.dispatch({ type: 'PEEK_CLEAR' });
          }
        }
        gesture.hasPeeked = false;
      }
    },
    [clearPeekTimer, engine],
  );

  return {
    handleCardPress,
    setHolding,
  };
}
