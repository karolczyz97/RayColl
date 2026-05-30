import { useState, useCallback, useRef, useEffect, useMemo, useReducer } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '../types/models';
import { calculateFsrs, mapMatchToRating, matchSpeech } from '../srs/srsEngine';
import { ttsService } from '../services/ttsService';
import { getSttService } from '../services/sttService';
import {
  playErrorSound,
  playMicOffSound,
  playMicOnSound,
  playSuccessSound,
} from '../services/audioFeedback';
import { useAppTheme } from '../contexts/ThemeContext';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from '../features/study/session/sessionReducer';
import {
  areAllActivePagesRevealed,
  getActivePageIndexes,
  getNextHiddenPageIndex,
  sleep,
  uniquePageIndexes,
} from '../features/study/session/sessionUtils';
import type { SessionAction } from '../features/study/session/sessionTypes';

export function useStudySession(
  group: FlashcardGroup | null,
  steps: ModeStep[],
  onCardReviewed: (groupId: string, card: Flashcard) => void,
) {
  const [dueCards, setDueCards] = useState<Flashcard[]>([]);
  const [state, dispatch] = useReducer(sessionReducer, INITIAL_STUDY_SESSION_STATE);
  const [failedCount, setFailedCount] = useState(0);
  const { ttsRate } = useAppTheme();
  const abortRef = useRef(false);
  const isMountedRef = useRef(true);
  const sttService = useRef(getSttService());
  const lastTtsDurationRef = useRef(0);
  const holdingRef = useRef(false);
  const allCardsRef = useRef<Flashcard[]>([]);
  const failedCardsRef = useRef<Flashcard[]>([]);
  const reviewedCardIdsRef = useRef<Set<string>>(new Set());
  const groupRef = useRef(group);
  const activeStepsRef = useRef<ModeStep[]>([]);
  const stateRef = useRef(state);
  const ttsRateRef = useRef(ttsRate);
  const onCardReviewedRef = useRef(onCardReviewed);
  const lastExecutedCardIndexRef = useRef<number | null>(null);
  const dueCardsRef = useRef(dueCards);
  const activePageCount = group?.activePageCount ?? Infinity;

  const dispatchIfMounted = useCallback((action: SessionAction) => {
    if (isMountedRef.current) {
      dispatch(action);
    }
  }, []);

  useEffect(() => {
    const currentSttService = sttService.current;
    return () => {
      isMountedRef.current = false;
      abortRef.current = true;
      ttsService.cancel();
      void currentSttService.stopListening();
    };
  }, []);

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
    groupRef.current = group;
  }, [group]);
  useEffect(() => {
    activeStepsRef.current = activeSteps;
  }, [activeSteps]);
  useEffect(() => {
    stateRef.current = state;
  }, [state]);
  useEffect(() => {
    ttsRateRef.current = ttsRate;
  }, [ttsRate]);
  useEffect(() => {
    onCardReviewedRef.current = onCardReviewed;
  }, [onCardReviewed]);
  useEffect(() => {
    dueCardsRef.current = dueCards;
  }, [dueCards]);

  const startSession = useCallback(
    (cards: Flashcard[], clearReviewed = true) => {
      abortRef.current = false;
      allCardsRef.current = cards;
      failedCardsRef.current = [];
      setFailedCount(0);
      lastExecutedCardIndexRef.current = null;
      if (clearReviewed) {
        reviewedCardIdsRef.current.clear();
      }
      setDueCards(cards);
      dispatchIfMounted({ type: 'START_SESSION', cards });
    },
    [dispatchIfMounted],
  );

  const processCardReview = useCallback((card: Flashcard, rating: number) => {
    const currentGroup = groupRef.current;
    if (!currentGroup) return;
    if (!reviewedCardIdsRef.current.has(card.id)) {
      reviewedCardIdsRef.current.add(card.id);
      const updated: Flashcard = {
        ...card,
        srsState: calculateFsrs(card.srsState, rating),
      };
      onCardReviewedRef.current(currentGroup.id, updated);
    }
  }, []);

  const waitUntilReleased = useCallback(async () => {
    while (holdingRef.current) {
      await sleep(100);
    }
  }, []);

  const advanceToNextCard = useCallback(async () => {
    await waitUntilReleased();
    const nextIndex = stateRef.current.currentCardIndex + 1;
    if (nextIndex >= dueCardsRef.current.length) {
      dispatchIfMounted({ type: 'FINISH_SESSION' });
    } else {
      dispatchIfMounted({ type: 'ADVANCE_CARD', nextCardIndex: nextIndex });
    }
  }, [dispatchIfMounted, waitUntilReleased]);

  const playTts = useCallback(
    async (text: string, lang: string) => {
      const startTime = Date.now();
      try {
        await ttsService.speak({ text, lang, rate: ttsRateRef.current });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.error('TTS Speak Error:', err);
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: `TTS Error: ${message}` });
      }
      lastTtsDurationRef.current = Date.now() - startTime;
    },
    [dispatchIfMounted],
  );

  const runSpeechRecognition = useCallback(
    async (lang: string, timeoutMs: number) => {
      playMicOnSound();
      let recognized = '';
      try {
        recognized = await sttService.current.startListening({
          language: lang,
          timeoutMs,
          onPartialResult: (text) => dispatchIfMounted({ type: 'UPDATE_PARTIAL_STT', text }),
          onListeningStateChange: (listening) => {
            if (!listening) {
              playMicOffSound();
            }
          },
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.error('STT Listen Error:', err);
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: `STT Error: ${message}` });
      }
      return recognized;
    },
    [dispatchIfMounted],
  );

  const executeStepRef = useRef<(card: Flashcard, stepIndex: number) => Promise<void>>(undefined);

  const executeStep = useCallback(
    async (card: Flashcard, stepIndex: number) => {
      const currentGroup = groupRef.current;
      const currentSteps = activeStepsRef.current;
      const currentState = stateRef.current;

      if (abortRef.current || !currentGroup || stepIndex >= currentSteps.length) {
        dispatchIfMounted({ type: 'SHOW_RATINGS' });
        return;
      }

      const step = currentSteps[stepIndex];

      switch (step.type) {
        case 'show_page': {
          // Reveal additively in the reducer so back-to-back show_page steps
          // can't clobber each other with a stale revealedPages snapshot.
          dispatchIfMounted({ type: 'REVEAL_PAGE', stepIndex, pageIndex: step.pageIndex });
          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'reveal_on_tap': {
          // Stop the sequence and wait for a tap; handleCardTap reveals the next
          // hidden page(s) and resumes once everything active is shown.
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex, waitingForTap: true });
          break;
        }

        case 'rate': {
          dispatchIfMounted({ type: 'SHOW_RATINGS' });
          break;
        }

        case 'speak_page': {
          dispatchIfMounted({ type: 'START_SPEAKING', stepIndex });
          await playTts(card.pages[step.pageIndex] || '', currentGroup.pageLanguages[step.pageIndex] || 'en-US');
          if (step.extraPauseMs > 0) {
            await sleep(step.extraPauseMs);
          }
          dispatchIfMounted({ type: 'END_SPEAKING' });

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'dynamic_pause': {
          const text = card.pages[step.nextPageIndex] || '';
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex });
          await sleep(text.length * 60 + (step.extraPauseMs || 0));

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'wait': {
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex });
          await sleep(step.ms);

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIndex + 1);
          }
          break;
        }

        case 'listen_and_branch': {
          dispatchIfMounted({ type: 'START_LISTENING', stepIndex });
          const lang = currentGroup.pageLanguages[step.pageIndex] || 'en-US';
          const softTimeout = Math.max(5000, lastTtsDurationRef.current * 3);
          const recognized = await runSpeechRecognition(lang, softTimeout + 10000);
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
            if (!failedCardsRef.current.find((item) => item.id === card.id)) {
              failedCardsRef.current.push(card);
              if (isMountedRef.current) {
                setFailedCount(failedCardsRef.current.length);
              }
            }
          }

          if (percent < step.successThreshold) {
            dispatchIfMounted({
              type: 'REVEAL_PAGES',
              revealedPages: getActivePageIndexes(currentGroup),
            });

            if (step.incorrectTtsPageIndex !== undefined) {
              dispatchIfMounted({ type: 'START_SPEAKING', stepIndex });
              const correctionStart = Date.now();
              await playTts(
                card.pages[step.incorrectTtsPageIndex] || '',
                currentGroup.pageLanguages[step.incorrectTtsPageIndex] || 'en-US',
              );
              const correctionDuration = Date.now() - correctionStart;
              dispatchIfMounted({ type: 'END_SPEAKING' });
              await sleep(correctionDuration * 2);
            } else {
              await sleep(2000);
            }

            if (!abortRef.current) {
              await waitUntilReleased();
              processCardReview(card, 1);
              const nextIndex = currentState.currentCardIndex + 1;
              if (nextIndex >= dueCardsRef.current.length) {
                dispatchIfMounted({ type: 'FINISH_SESSION' });
              } else {
                dispatchIfMounted({ type: 'ADVANCE_CARD', nextCardIndex: nextIndex });
              }
              return;
            }
          }
          break;
        }
      }
    },
    [advanceToNextCard, dispatchIfMounted, playTts, processCardReview, runSpeechRecognition, waitUntilReleased],
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

  const handleCardTap = useCallback(() => {
    const currentGroup = groupRef.current;
    const currentState = stateRef.current;
    if (!currentState.waitingForTap || !currentGroup) return;
    const card = dueCardsRef.current[currentState.currentCardIndex];
    if (!card) return;

    const stepIndex = currentState.currentStepIndex;
    const nextHiddenPageIndex = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);

    if (nextHiddenPageIndex === null) {
      // Nothing left to reveal — treat the tap as "continue" past the gate.
      dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex, waitingForTap: false });
      resumeAfterStep(card, stepIndex + 1);
      return;
    }

    const nextRevealedPages = uniquePageIndexes([...currentState.revealedPages, nextHiddenPageIndex]);
    const allRevealed = areAllActivePagesRevealed(currentGroup, nextRevealedPages);
    dispatchIfMounted({
      type: 'SET_CURRENT_STEP',
      stepIndex,
      revealedPages: nextRevealedPages,
      waitingForTap: !allRevealed,
    });
    if (allRevealed) {
      resumeAfterStep(card, stepIndex + 1);
    }
  }, [dispatchIfMounted, resumeAfterStep]);

  const setHolding = useCallback((holding: boolean) => {
    holdingRef.current = holding;
  }, []);

  const getFreshCards = useCallback((cards: Flashcard[]) => {
    const currentGroup = groupRef.current;
    if (!currentGroup) return cards;

    const cardsById = new Map(currentGroup.cards.map((card) => [card.id, card]));
    return cards.map((card) => cardsById.get(card.id) ?? card);
  }, []);

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

  const handleRating = useCallback(
    async (rating: number) => {
      if (!groupRef.current || dueCardsRef.current.length === 0) return;
      const card = dueCardsRef.current[stateRef.current.currentCardIndex];
      if (rating === 1 && !failedCardsRef.current.find((item) => item.id === card.id)) {
        failedCardsRef.current.push(card);
        if (isMountedRef.current) {
          setFailedCount(failedCardsRef.current.length);
        }
      }
      processCardReview(card, rating);
      await advanceToNextCard();
    },
    [advanceToNextCard, processCardReview],
  );

  const restartSession = useCallback(() => {
    startSession(getFreshCards(allCardsRef.current), true);
  }, [getFreshCards, startSession]);

  const restartFailed = useCallback(() => {
    if (failedCardsRef.current.length > 0) {
      startSession(getFreshCards(failedCardsRef.current), true);
    }
  }, [getFreshCards, startSession]);

  const stopSession = useCallback(() => {
    abortRef.current = true;
    ttsService.cancel();
    void sttService.current.stopListening();
  }, []);

  const compatibilityState = useMemo(
    () => ({
      currentCardIndex: state.currentCardIndex,
      currentStepIndex: state.currentStepIndex,
      revealedPages: state.revealedPages,
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
    handleCardTap,
    startSession,
    stopSession,
    setHolding,
    restartSession,
    restartFailed,
    failedCount,
  };
}
