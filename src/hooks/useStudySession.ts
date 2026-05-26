import { useState, useCallback, useRef, useEffect, useMemo, useReducer } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '../types/models';
import { calculateFsrs, matchSpeech, mapMatchToRating } from '../srs/srsEngine';
import { ttsService } from '../services/ttsService';
import { getSttService } from '../services/sttService';
import {
  playMicOnSound,
  playMicOffSound,
  playSuccessSound,
  playErrorSound,
} from '../services/audioFeedback';
import { useAppTheme } from '../contexts/ThemeContext';

export type SessionStatus =
  | 'idle'
  | 'speaking'
  | 'listening'
  | 'checking'
  | 'revealed'
  | 'finished'
  | 'error';

export interface StudySessionState {
  status: SessionStatus;
  currentCardIndex: number;
  currentStepIndex: number;
  revealedPages: number[];
  sttResultText: string;
  sttMatchPercent: number;
  waitingForTap: boolean;
  errorMsg?: string;
}

const INITIAL_STATE: StudySessionState = {
  status: 'idle',
  currentCardIndex: 0,
  currentStepIndex: 0,
  revealedPages: [],
  sttResultText: '',
  sttMatchPercent: 0,
  waitingForTap: false,
  errorMsg: undefined,
};

type SessionAction =
  | { type: 'START_SESSION'; cards: Flashcard[] }
  | {
      type: 'SET_CURRENT_STEP';
      stepIndex: number;
      revealedPages?: number[];
      waitingForTap?: boolean;
    }
  | { type: 'START_SPEAKING'; stepIndex: number }
  | { type: 'END_SPEAKING' }
  | { type: 'START_LISTENING'; stepIndex: number }
  | { type: 'UPDATE_PARTIAL_STT'; text: string }
  | { type: 'END_LISTENING'; text: string; matchPercent: number }
  | { type: 'REVEAL_PAGES'; revealedPages: number[]; waitingForTap?: boolean }
  | { type: 'SHOW_RATINGS' }
  | { type: 'FINISH_SESSION' }
  | { type: 'ADVANCE_CARD'; nextCardIndex: number }
  | { type: 'SET_ERROR'; errorMsg: string }
  | { type: 'CLEAR_ERROR' };

function sessionReducer(state: StudySessionState, action: SessionAction): StudySessionState {
  switch (action.type) {
    case 'START_SESSION':
      return {
        ...INITIAL_STATE,
      };
    case 'SET_CURRENT_STEP':
      return {
        ...state,
        currentStepIndex: action.stepIndex,
        revealedPages: action.revealedPages ?? state.revealedPages,
        waitingForTap: action.waitingForTap ?? state.waitingForTap,
      };
    case 'START_SPEAKING':
      return {
        ...state,
        status: 'speaking',
        currentStepIndex: action.stepIndex,
      };
    case 'END_SPEAKING':
      return {
        ...state,
        status: 'idle',
      };
    case 'START_LISTENING':
      return {
        ...state,
        status: 'listening',
        currentStepIndex: action.stepIndex,
        sttResultText: '',
        sttMatchPercent: 0,
      };
    case 'UPDATE_PARTIAL_STT':
      return {
        ...state,
        sttResultText: action.text,
      };
    case 'END_LISTENING':
      return {
        ...state,
        status: 'checking',
        sttResultText: action.text,
        sttMatchPercent: action.matchPercent,
      };
    case 'REVEAL_PAGES':
      return {
        ...state,
        revealedPages: action.revealedPages,
        waitingForTap: action.waitingForTap ?? state.waitingForTap,
      };
    case 'SHOW_RATINGS':
      return {
        ...state,
        status: 'revealed',
        waitingForTap: false,
      };
    case 'FINISH_SESSION':
      return {
        ...state,
        status: 'finished',
        waitingForTap: false,
      };
    case 'ADVANCE_CARD':
      return {
        ...INITIAL_STATE,
        currentCardIndex: action.nextCardIndex,
      };
    case 'SET_ERROR':
      return {
        ...state,
        status: 'error',
        errorMsg: action.errorMsg,
      };
    case 'CLEAR_ERROR':
      return {
        ...state,
        errorMsg: undefined,
        status: 'idle',
      };
    default:
      return state;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function uniquePageIndexes(pageIndexes: number[]): number[] {
  return [...new Set(pageIndexes)].sort((a, b) => a - b);
}

function getActivePageIndexes(group: FlashcardGroup): number[] {
  const activePageCount = group.activePageCount ?? group.pageNames.length;
  return group.pageNames.slice(0, activePageCount).map((_, index) => index);
}

function getNextHiddenPageIndex(group: FlashcardGroup, revealedPages: number[]): number | null {
  const revealed = new Set([0, ...revealedPages]);
  return getActivePageIndexes(group).find((pageIndex) => !revealed.has(pageIndex)) ?? null;
}

function areAllActivePagesRevealed(group: FlashcardGroup, revealedPages: number[]): boolean {
  const revealed = new Set([0, ...revealedPages]);
  return getActivePageIndexes(group).every((pageIndex) => revealed.has(pageIndex));
}

export function useStudySession(
  group: FlashcardGroup | null,
  steps: ModeStep[],
  onCardReviewed: (groupId: string, card: Flashcard) => void,
) {
  const [dueCards, setDueCards] = useState<Flashcard[]>([]);
  const [state, dispatch] = useReducer(sessionReducer, INITIAL_STATE);
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

  // Refs for stable executeStep dependencies
  const groupRef = useRef(group);
  const activeStepsRef = useRef<ModeStep[]>([]);
  const stateRef = useRef(state);
  const ttsRateRef = useRef(ttsRate);
  const onCardReviewedRef = useRef(onCardReviewed);
  const lastExecutedCardIndexRef = useRef<number | null>(null);

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

  // Filter out steps referencing hidden pages based on activePageCount
  const activePageCount = group?.activePageCount ?? group?.pageNames.length ?? 99;
  const activeSteps = useMemo(() => {
    return steps.filter((step) => {
      if ('pageIndex' in step && step.pageIndex >= activePageCount) return false;
      if ('nextPageIndex' in step && step.nextPageIndex >= activePageCount) return false;
      return true;
    });
  }, [steps, activePageCount]);

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

  // Keep dueCards ref to avoid rebuild dependencies in callbacks
  const dueCardsRef = useRef(dueCards);
  useEffect(() => {
    dueCardsRef.current = dueCards;
  }, [dueCards]);

  const startSession = useCallback(
    (cards: Flashcard[], clearReviewed = true) => {
      abortRef.current = false;
      allCardsRef.current = cards;
      failedCardsRef.current = [];
      setFailedCount(0);
      lastExecutedCardIndexRef.current = null; // Reset execution tracker
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
    const nextIdx = stateRef.current.currentCardIndex + 1;
    if (nextIdx >= dueCardsRef.current.length) {
      dispatchIfMounted({ type: 'FINISH_SESSION' });
    } else {
      dispatchIfMounted({ type: 'ADVANCE_CARD', nextCardIndex: nextIdx });
    }
  }, [dispatchIfMounted, waitUntilReleased]);

  // TTS Flow Helper
  const playTts = useCallback(
    async (text: string, lang: string) => {
      const startTime = Date.now();
      try {
        await ttsService.speak({ text, lang, rate: ttsRateRef.current });
      } catch (err) {
        const errMsg = err instanceof Error ? err.message : String(err);
        console.error('TTS Speak Error:', err);
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: `TTS Error: ${errMsg}` });
      }
      lastTtsDurationRef.current = Date.now() - startTime;
    },
    [dispatchIfMounted],
  );

  // STT Flow Helper
  const runSpeechRecognition = useCallback(
    async (lang: string, timeoutMs: number) => {
      playMicOnSound();
      let recognized = '';
      try {
        recognized = await sttService.current.startListening({
          language: lang,
          timeoutMs,
          onPartialResult: (t) => dispatchIfMounted({ type: 'UPDATE_PARTIAL_STT', text: t }),
          onListeningStateChange: (listening) => {
            if (!listening) {
              playMicOffSound();
            }
          },
        });
      } catch (err) {
        const errMsg = err instanceof Error ? err.message : String(err);
        console.error('STT Listen Error:', err);
        dispatchIfMounted({ type: 'SET_ERROR', errorMsg: `STT Error: ${errMsg}` });
      }
      return recognized;
    },
    [dispatchIfMounted],
  );

  // Recursion via ref to avoid hoisting circular dependency in useCallback
  const executeStepRef = useRef<(card: Flashcard, stepIdx: number) => Promise<void>>(undefined);

  const executeStep = useCallback(
    async (card: Flashcard, stepIdx: number) => {
      const currentGroup = groupRef.current;
      const currentSteps = activeStepsRef.current;
      const currentState = stateRef.current;

      if (abortRef.current || !currentGroup || stepIdx >= currentSteps.length) {
        dispatchIfMounted({ type: 'SHOW_RATINGS' });
        return;
      }
      const step = currentSteps[stepIdx];

      switch (step.type) {
        case 'show_page': {
          const alreadyRevealed = currentState.revealedPages;
          const nextRevealed = uniquePageIndexes([...alreadyRevealed, step.pageIndex]);

          if (stepIdx === currentSteps.length - 1) {
            dispatchIfMounted({
              type: 'SET_CURRENT_STEP',
              stepIndex: stepIdx,
              revealedPages: alreadyRevealed,
              waitingForTap: true,
            });
            return;
          }

          dispatchIfMounted({
            type: 'SET_CURRENT_STEP',
            stepIndex: stepIdx,
            revealedPages: nextRevealed,
          });

          await executeStepRef.current?.(card, stepIdx + 1);
          break;
        }

        case 'speak_page': {
          dispatchIfMounted({ type: 'START_SPEAKING', stepIndex: stepIdx });
          const lang = currentGroup.pageLanguages[step.pageIndex] || 'en-US';
          const text = card.pages[step.pageIndex] || '';

          await playTts(text, lang);

          if (step.extraPauseMs > 0) {
            await sleep(step.extraPauseMs);
          }
          dispatchIfMounted({ type: 'END_SPEAKING' });

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIdx + 1);
          }
          break;
        }

        case 'dynamic_pause': {
          const text = card.pages[step.nextPageIndex] || '';
          const pauseMs = text.length * 60 + (step.extraPauseMs || 0);
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex: stepIdx });
          await sleep(pauseMs);

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIdx + 1);
          }
          break;
        }

        case 'wait': {
          dispatchIfMounted({ type: 'SET_CURRENT_STEP', stepIndex: stepIdx });
          await sleep(step.ms);

          if (!abortRef.current) {
            await executeStepRef.current?.(card, stepIdx + 1);
          }
          break;
        }

        case 'listen_and_branch': {
          dispatchIfMounted({ type: 'START_LISTENING', stepIndex: stepIdx });
          const lang = currentGroup.pageLanguages[step.pageIndex] || 'en-US';
          const softTimeout = Math.max(5000, lastTtsDurationRef.current * 3);

          const recognized = await runSpeechRecognition(lang, softTimeout + 10000);

          // Compute match
          const original = card.pages[step.pageIndex] || '';
          const percent = matchSpeech(recognized, original);

          dispatchIfMounted({
            type: 'END_LISTENING',
            text: recognized || '',
            matchPercent: percent,
          });

          // Show result for a moment
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
            if (!failedCardsRef.current.find((c) => c.id === card.id)) {
              failedCardsRef.current.push(card);
              if (isMountedRef.current) {
                setFailedCount(failedCardsRef.current.length);
              }
            }
          }

          if (percent < step.successThreshold) {
            const allPages = getActivePageIndexes(currentGroup);
            dispatchIfMounted({ type: 'REVEAL_PAGES', revealedPages: allPages });

            if (step.incorrectTtsPageIndex !== undefined) {
              const corrLang = currentGroup.pageLanguages[step.incorrectTtsPageIndex] || 'en-US';
              dispatchIfMounted({ type: 'START_SPEAKING', stepIndex: stepIdx });
              const corrStart = Date.now();
              await playTts(card.pages[step.incorrectTtsPageIndex] || '', corrLang);
              const corrDuration = Date.now() - corrStart;
              dispatchIfMounted({ type: 'END_SPEAKING' });
              await sleep(corrDuration * 2);
            } else {
              await sleep(2000);
            }

            if (!abortRef.current) {
              await waitUntilReleased();
              processCardReview(card, 1);

              const nextIdx = currentState.currentCardIndex + 1;
              if (nextIdx >= dueCardsRef.current.length) {
                dispatchIfMounted({ type: 'FINISH_SESSION' });
              } else {
                dispatchIfMounted({ type: 'ADVANCE_CARD', nextCardIndex: nextIdx });
              }
              return;
            }
          }
          break;
        }
      }
    },
    [
      playTts,
      runSpeechRecognition,
      processCardReview,
      advanceToNextCard,
      waitUntilReleased,
      dispatchIfMounted,
    ],
  );

  // Sync ref to current executeStep callback function
  useEffect(() => {
    executeStepRef.current = executeStep;
  }, [executeStep]);

  const handleCardTap = useCallback(() => {
    const currentGroup = groupRef.current;
    if (!stateRef.current.waitingForTap || !currentGroup) return;
    const currentRevealedPages = stateRef.current.revealedPages;
    const nextHiddenPageIndex = getNextHiddenPageIndex(currentGroup, currentRevealedPages);
    if (nextHiddenPageIndex === null) {
      dispatchIfMounted({ type: 'SHOW_RATINGS' });
      return;
    }

    const nextRevealedPages = uniquePageIndexes([...currentRevealedPages, nextHiddenPageIndex]);
    dispatchIfMounted({
      type: 'SET_CURRENT_STEP',
      stepIndex: stateRef.current.currentStepIndex,
      revealedPages: nextRevealedPages,
      waitingForTap: !areAllActivePagesRevealed(currentGroup, nextRevealedPages),
    });
    if (areAllActivePagesRevealed(currentGroup, nextRevealedPages)) {
      dispatchIfMounted({ type: 'SHOW_RATINGS' });
    }
  }, [dispatchIfMounted]);

  const setHolding = useCallback((holding: boolean) => {
    holdingRef.current = holding;
  }, []);

  useEffect(() => {
    if (dueCards.length === 0 || state.status === 'finished') {
      lastExecutedCardIndexRef.current = null;
      return;
    }
    if (state.status === 'revealed' || state.waitingForTap) return;
    const card = dueCards[state.currentCardIndex];
    if (!card) return;

    if (lastExecutedCardIndexRef.current === state.currentCardIndex) {
      return;
    }
    lastExecutedCardIndexRef.current = state.currentCardIndex;

    let active = true;
    const timer = setTimeout(() => {
      if (active) {
        executeStep(card, 0);
      }
    }, 0);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [state.currentCardIndex, state.status, state.waitingForTap, dueCards, executeStep]);

  const handleRating = useCallback(
    async (rating: number) => {
      if (!groupRef.current || dueCardsRef.current.length === 0) return;
      const card = dueCardsRef.current[stateRef.current.currentCardIndex];
      if (rating === 1 && !failedCardsRef.current.find((c) => c.id === card.id)) {
        failedCardsRef.current.push(card);
        if (isMountedRef.current) {
          setFailedCount(failedCardsRef.current.length);
        }
      }
      processCardReview(card, rating);
      await advanceToNextCard();
    },
    [processCardReview, advanceToNextCard],
  );

  const restartSession = useCallback(() => {
    startSession(allCardsRef.current, true);
  }, [startSession]);

  const restartFailed = useCallback(() => {
    if (failedCardsRef.current.length > 0) {
      startSession([...failedCardsRef.current], false);
    }
  }, [startSession]);

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
