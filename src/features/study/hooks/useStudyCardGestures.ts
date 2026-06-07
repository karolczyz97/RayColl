import { useCallback, useEffect, useRef } from 'react';
import type { MutableRefObject } from 'react';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import type { StudySkipState } from '@/features/study/hooks/useStudyAudio';
import {
  areAllActivePagesRevealed,
  getNextHiddenPageIndex,
  PEEK_HOLD_THRESHOLD_MS,
  uniquePageIndexes,
} from '@/features/study/session/sessionUtils';
import type {
  SessionAction,
  StudySessionState,
} from '@/features/study/session/sessionTypes';
import {
  playCardPeekHaptic,
  playSelectionHaptic,
  playStudyActionHaptic,
} from '@/services/hapticFeedback';

interface UseStudyCardGesturesParams {
  groupRef: MutableRefObject<FlashcardGroup | null>;
  stateRef: MutableRefObject<StudySessionState>;
  dueCardsRef: MutableRefObject<Flashcard[]>;
  holdingRef: MutableRefObject<boolean>;
  skipRef: MutableRefObject<StudySkipState>;
  requestSkip: () => void;
  resumeAfterStep: (card: Flashcard, nextStepIndex: number) => void;
  dispatchIfMounted: (action: SessionAction) => void;
}

interface GestureState {
  pressTime: number;
  hasPeeked: boolean;
  blockPress: boolean;
  peekTimer: ReturnType<typeof setTimeout> | null;
}

export function useStudyCardGestures({
  groupRef,
  stateRef,
  dueCardsRef,
  holdingRef,
  skipRef,
  requestSkip,
  resumeAfterStep,
  dispatchIfMounted,
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
    if (gesture.blockPress) {
      gesture.blockPress = false;
      return;
    }
    const currentGroup = groupRef.current;
    const currentState = stateRef.current;
    if (!currentGroup) return;

    if (currentState.waitingForTap) {
      const card = dueCardsRef.current[currentState.currentCardIndex];
      if (!card) return;
      const stepIndex = currentState.currentStepIndex;
      const nextHiddenPageIndex = getNextHiddenPageIndex(
        currentGroup,
        currentState.revealedPages,
      );

      if (nextHiddenPageIndex === null) {
        playSelectionHaptic();
        dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex, waitingForTap: false });
        resumeAfterStep(card, stepIndex + 1);
        return;
      }

      const nextRevealedPages = uniquePageIndexes([
        ...currentState.revealedPages,
        nextHiddenPageIndex,
      ]);
      const allRevealed = areAllActivePagesRevealed(currentGroup, nextRevealedPages);
      playSelectionHaptic();
      dispatchIfMounted({
        type: 'SET_CURRENT_STEP',
        stepIndex,
        revealedPages: nextRevealedPages,
        waitingForTap: !allRevealed,
      });
      if (allRevealed) {
        resumeAfterStep(card, stepIndex + 1);
      }
      return;
    }

    if (currentState.status === 'revealed' || currentState.status === 'finished') return;

    if (skipRef.current.armed) {
      playStudyActionHaptic();
      dispatchIfMounted({ type: 'PEEK_CLEAR' });
      requestSkip();
    }
  }, [
    dispatchIfMounted,
    dueCardsRef,
    groupRef,
    requestSkip,
    resumeAfterStep,
    skipRef,
    stateRef,
  ]);

  const setHolding = useCallback(
    (holding: boolean) => {
      holdingRef.current = holding;
      const gesture = gestureRef.current;
      const currentGroup = groupRef.current;
      const currentState = stateRef.current;

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
              dispatchIfMounted({ type: 'PEEK_SET', pageIndex: nextHidden });
            }, PEEK_HOLD_THRESHOLD_MS);
          }
        }
      } else {
        clearPeekTimer();
        const holdDuration = Date.now() - gesture.pressTime;

        if (holdDuration < PEEK_HOLD_THRESHOLD_MS) {
          if (
            currentGroup &&
            currentState.status !== 'revealed' &&
            currentState.status !== 'finished'
          ) {
            const nextHidden = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);
            if (nextHidden !== null) {
              const nextRevealedPages = uniquePageIndexes([
                ...currentState.revealedPages,
                nextHidden,
              ]);
              dispatchIfMounted({
                type: 'SET_CURRENT_STEP',
                stepIndex: currentState.currentStepIndex,
                revealedPages: nextRevealedPages,
              });
              if (!currentState.waitingForTap) {
                playSelectionHaptic();
              }
            }
          }
          gesture.blockPress = false;
        } else {
          gesture.blockPress = true;
          if (gesture.hasPeeked) {
            dispatchIfMounted({ type: 'PEEK_CLEAR' });
          }
        }
        gesture.hasPeeked = false;
      }
    },
    [clearPeekTimer, dispatchIfMounted, groupRef, holdingRef, stateRef],
  );

  return {
    handleCardPress,
    setHolding,
  };
}
