import { useCallback, useRef, useEffect, useMemo, useReducer } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '@/types/models';
import { mapMatchToRating, matchSpeech } from '@/srs/srsEngine';
import {
  playErrorSound,
  playSuccessSound,
} from '@/services/audioFeedback';
import { useAppTheme } from '@/contexts/ThemeContext';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from '@/features/study/session/sessionReducer';
import { useSyncedRef } from './useSyncedRef';
import {
  getActivePageIndexes,
  sleep,
} from '@/features/study/session/sessionUtils';
import type { SessionAction } from '@/features/study/session/sessionTypes';
import { useStudyAudio } from '@/features/study/hooks/useStudyAudio';
import { useStudyCardGestures } from '@/features/study/hooks/useStudyCardGestures';
import { useStudyReviewFlow } from '@/features/study/hooks/useStudyReviewFlow';

export function useStudySession(
  group: FlashcardGroup | null,
  steps: ModeStep[],
  onCardReviewed: (groupId: string, card: Flashcard) => void,
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
      const currentGroup = groupRef.current;
      const currentSteps = activeStepsRef.current;

      skipRef.current.requested = false;
      skipRef.current.armed = false;

      if (abortRef.current || !currentGroup || stepIndex >= currentSteps.length) {
        dispatchIfMounted({ type: 'SHOW_RATINGS' });
        return;
      }

      const step = currentSteps[stepIndex];

      switch (step.type) {
        case 'show_page': {
          dispatchIfMounted({ type: 'REVEAL_PAGE', stepIndex, pageIndex: step.pageIndex });
          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'reveal_on_tap': {
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex, waitingForTap: true });
          break;
        }

        case 'rate': {
          dispatchIfMounted({ type: 'SHOW_RATINGS' });
          break;
        }

        case 'speak_page': {
          skipRef.current.armed = true;
          dispatchIfMounted({ type: 'START_SPEAKING', stepIndex });
          await guardedAwait(playTts(card.pages[step.pageIndex] || '', currentGroup.pageLanguages[step.pageIndex] || 'en-US'));
          if (!skipRef.current.requested && step.extraPauseMs > 0) {
            await guardedAwait(sleep(step.extraPauseMs));
          }
          dispatchIfMounted({ type: 'END_SPEAKING' });

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'dynamic_pause': {
          skipRef.current.armed = true;
          const text = card.pages[step.nextPageIndex] || '';
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex });
          await guardedAwait(sleep(text.length * 60 + (step.extraPauseMs || 0)));

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'wait': {
          skipRef.current.armed = true;
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex });
          await guardedAwait(sleep(step.ms));

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'listen_and_branch': {
          skipRef.current.armed = true;
          dispatchIfMounted({ type: 'START_LISTENING', stepIndex });
          const lang = currentGroup.pageLanguages[step.pageIndex] || 'en-US';
          const softTimeout = Math.max(5000, lastTtsDurationRef.current * 3);
          const recognized = (await guardedAwait(runSpeechRecognition(lang, softTimeout + 10000))) || '';
          skipRef.current.armed = false;

          if (skipRef.current.requested) {
            dispatchIfMounted({
              type: 'END_LISTENING',
              text: '',
              matchPercent: 0,
            });
            if (!abortRef.current) {
              await executeStepRef.current?.(card, stepIndex + 1);
            }
            break;
          }

          const original = card.pages[step.pageIndex] || '';
          const percent = matchSpeech(recognized, original);

          dispatchIfMounted({
            type: 'END_LISTENING',
            text: recognized || '',
            matchPercent: percent,
          });

          await sleep(1200);

          if (percent >= step.successThreshold) {
            playSuccessSound();
            const autoRating = mapMatchToRating(percent);
            await sleep(600);
            if (!abortRef.current) {
              processCardReview(card, autoRating);
              await advanceToNextCard();
              return;
            }
          } else {
            playErrorSound();
            markCardFailed(card);
          }

          if (percent < step.successThreshold) {
            dispatchIfMounted({
              type: 'REVEAL_PAGES',
              revealedPages: getActivePageIndexes(currentGroup),
            });

            skipRef.current.armed = true;
            if (step.incorrectTtsPageIndex !== undefined) {
              dispatchIfMounted({ type: 'START_SPEAKING', stepIndex });
              const correctionStart = Date.now();
              await guardedAwait(playTts(
                card.pages[step.incorrectTtsPageIndex] || '',
                currentGroup.pageLanguages[step.incorrectTtsPageIndex] || 'en-US',
              ));
              const correctionDuration = Date.now() - correctionStart;
              dispatchIfMounted({ type: 'END_SPEAKING' });
              if (!skipRef.current.requested) {
                await guardedAwait(sleep(correctionDuration * 2));
              }
            } else {
              await guardedAwait(sleep(2000));
            }
            skipRef.current.armed = false;
            if (skipRef.current.requested) {
              unmarkCardFailed(card.id);
              if (!abortRef.current) {
                await executeStepRef.current?.(card, stepIndex + 1);
              }
              break;
            }

            if (!abortRef.current) {
              processCardReview(card, 1);
              await advanceToNextCard();
              return;
            }
          }
          break;
        }
      }
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
