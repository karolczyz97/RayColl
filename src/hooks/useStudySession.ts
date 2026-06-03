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
import { useSyncedRef } from './useSyncedRef';
import {
  areAllActivePagesRevealed,
  getActivePageIndexes,
  getNextHiddenPageIndex,
  PEEK_HOLD_THRESHOLD_MS,
  sleep,
  uniquePageIndexes,
} from '../features/study/session/sessionUtils';
import {
  startReviewAttempt,
  tryMarkCardReviewed,
} from '../features/study/session/sessionReview';
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
  const reviewedAttemptKeysRef = useRef<Set<string>>(new Set());
  const sessionAttemptRef = useRef(0);
  const groupRef = useSyncedRef(group);
  const activeStepsRef = useRef<ModeStep[]>([]);
  const stateRef = useSyncedRef(state);
  const ttsRateRef = useSyncedRef(ttsRate);
  const onCardReviewedRef = useSyncedRef(onCardReviewed);
  const dueCardsRef = useSyncedRef(dueCards);
  const lastExecutedCardIndexRef = useRef<number | null>(null);
  const skipRef = useRef({ requested: false, armed: false, signalResolve: (() => {}) as () => void });
  const gestureRef = useRef({ pressTime: 0, hasPeeked: false, blockPress: false, peekTimer: null as ReturnType<typeof setTimeout> | null });
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
      void currentSttService.stopListening().catch(() => {});
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
    activeStepsRef.current = activeSteps;
  }, [activeSteps]);

  const startSession = useCallback(
    (cards: Flashcard[]) => {
      abortRef.current = false;
      allCardsRef.current = cards;
      failedCardsRef.current = [];
      setFailedCount(0);
      lastExecutedCardIndexRef.current = null;
      sessionAttemptRef.current = startReviewAttempt(
        reviewedAttemptKeysRef.current,
        sessionAttemptRef.current,
      );
      setDueCards(cards);
      dispatchIfMounted({ type: 'START_SESSION' });
    },
    [dispatchIfMounted],
  );

  const processCardReview = useCallback((card: Flashcard, rating: number) => {
    const currentGroup = groupRef.current;
    if (!currentGroup) return;
    if (
      tryMarkCardReviewed(reviewedAttemptKeysRef.current, sessionAttemptRef.current, card.id)
    ) {
      const updated: Flashcard = { ...card, srsState: calculateFsrs(card.srsState, rating) };
      onCardReviewedRef.current(currentGroup.id, updated);
    }
  }, [groupRef, onCardReviewedRef]);

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
  }, [dispatchIfMounted, dueCardsRef, stateRef, waitUntilReleased]);

  const playTts = useCallback(
    async (text: string, lang: string) => {
      const startTime = Date.now();
      try {
        await ttsService.speak({ text, lang, rate: ttsRateRef.current });
      } catch (err) {
        console.error('TTS Speak Error:', err);
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: 'study.error.tts' });
      }
      lastTtsDurationRef.current = Date.now() - startTime;
    },
    [dispatchIfMounted, ttsRateRef],
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
        console.error('STT Listen Error:', err);
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: 'study.error.stt' });
      }
      return recognized;
    },
    [dispatchIfMounted],
  );

  const requestSkip = useCallback(() => {
    const skip = skipRef.current;
    if (!skip.armed) return;
    skip.requested = true;
    ttsService.cancel();
    void sttService.current.stopListening().catch(() => {});
    skip.signalResolve();
  }, []);

  const guardedAwait = useCallback(async <T>(promise: Promise<T>): Promise<T | undefined> => {
    const skip = skipRef.current;
    const skipSignal = new Promise<void>((resolve) => {
      skip.signalResolve = resolve;
    });
    return (await Promise.race([promise, skipSignal]).catch(() => undefined)) as T | undefined;
  }, []);

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
              failedCardsRef.current = failedCardsRef.current.filter((c) => c.id !== card.id);
              if (isMountedRef.current) {
                setFailedCount(failedCardsRef.current.length);
              }
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
    [advanceToNextCard, dispatchIfMounted, groupRef, guardedAwait, playTts, processCardReview, runSpeechRecognition],
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
      const nextHiddenPageIndex = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);

      if (nextHiddenPageIndex === null) {
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
      return;
    }

    if (currentState.status === 'revealed' || currentState.status === 'finished') return;

    if (skipRef.current.armed) {
      dispatchIfMounted({ type: 'PEEK_CLEAR' });
      requestSkip();
    }
  }, [dispatchIfMounted, dueCardsRef, groupRef, requestSkip, resumeAfterStep, stateRef]);

  const setHolding = useCallback((holding: boolean) => {
    holdingRef.current = holding;
    const gesture = gestureRef.current;
    const currentGroup = groupRef.current;
    const currentState = stateRef.current;

    if (holding) {
      gesture.pressTime = Date.now();
      gesture.hasPeeked = false;
      gesture.blockPress = false;
      gesture.peekTimer = null;

      if (currentGroup && currentState.status !== 'revealed' && currentState.status !== 'finished') {
        const nextHidden = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);
        if (nextHidden !== null) {
          gesture.peekTimer = setTimeout(() => {
            gesture.hasPeeked = true;
            dispatchIfMounted({ type: 'PEEK_SET', pageIndex: nextHidden });
          }, PEEK_HOLD_THRESHOLD_MS);
        }
      }
    } else {
      clearTimeout(gesture.peekTimer!);
      gesture.peekTimer = null;
      const holdDuration = Date.now() - gesture.pressTime;

      if (holdDuration < PEEK_HOLD_THRESHOLD_MS) {
        // Short hold: permanently reveal the page
        if (currentGroup && currentState.status !== 'revealed' && currentState.status !== 'finished') {
          const nextHidden = getNextHiddenPageIndex(currentGroup, currentState.revealedPages);
          if (nextHidden !== null) {
            const nextRevealedPages = uniquePageIndexes([...currentState.revealedPages, nextHidden]);
            dispatchIfMounted({
              type: 'SET_CURRENT_STEP',
              stepIndex: currentState.currentStepIndex,
              revealedPages: nextRevealedPages,
            });
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
  }, [dispatchIfMounted, groupRef, stateRef]);

  const getFreshCards = useCallback((cards: Flashcard[]) => {
    const currentGroup = groupRef.current;
    if (!currentGroup) return cards;

    const cardsById = new Map(currentGroup.cards.map((card) => [card.id, card]));
    return cards.map((card) => cardsById.get(card.id) ?? card);
  }, [groupRef]);

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
      const index = stateRef.current.currentCardIndex;
      if (index >= dueCardsRef.current.length || !groupRef.current) return;
      const card = dueCardsRef.current[index];
      if (!card) return;
      if (rating === 1 && !failedCardsRef.current.find((item) => item.id === card.id)) {
        failedCardsRef.current.push(card);
        if (isMountedRef.current) {
          setFailedCount(failedCardsRef.current.length);
        }
      }
      processCardReview(card, rating);
      await advanceToNextCard();
    },
    [advanceToNextCard, dueCardsRef, groupRef, processCardReview, stateRef],
  );

  const restartSession = useCallback(() => {
    startSession(getFreshCards(allCardsRef.current));
  }, [getFreshCards, startSession]);

  const restartFailed = useCallback(() => {
    if (failedCardsRef.current.length > 0) {
      startSession(getFreshCards(failedCardsRef.current));
    }
  }, [getFreshCards, startSession]);

  const stopSession = useCallback(() => {
    abortRef.current = true;
    ttsService.cancel();
    void sttService.current.stopListening().catch(() => {});
  }, []);

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
