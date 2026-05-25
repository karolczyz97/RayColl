import { useState, useCallback, useRef, useEffect } from 'react';
import type { FlashcardGroup, Flashcard, ModeStep } from '../types/models';
import { calculateFsrs, matchSpeech, mapMatchToRating } from '../srs/srsEngine';
import { ttsService } from '../services/ttsService';
import { getSttService } from '../services/sttService';
import { playMicOnSound, playMicOffSound, playSuccessSound, playErrorSound } from '../services/audioFeedback';

export interface StudySessionState {
  currentCardIndex: number;
  currentStepIndex: number;
  revealedPages: number[];
  isTtsPlaying: boolean;
  isSttListening: boolean;
  sttResultText: string;
  sttMatchPercent: number;
  showRatingButtons: boolean;
  isSessionFinished: boolean;
  waitingForTap: boolean;
}

const INITIAL_STATE: StudySessionState = {
  currentCardIndex: 0, currentStepIndex: 0, revealedPages: [],
  isTtsPlaying: false, isSttListening: false, sttResultText: '', sttMatchPercent: 0,
  showRatingButtons: false, isSessionFinished: false, waitingForTap: false,
};

export function useStudySession(
  group: FlashcardGroup | null,
  steps: ModeStep[],
  onCardReviewed: (groupId: string, card: Flashcard) => void,
) {
  const [dueCards, setDueCards] = useState<Flashcard[]>([]);
  const [state, setState] = useState<StudySessionState>(INITIAL_STATE);
  const abortRef = useRef(false);
  const sttService = useRef(getSttService());
  const lastTtsDurationRef = useRef(0);
  const holdingRef = useRef(false);
  const allCardsRef = useRef<Flashcard[]>([]);
  const failedCardsRef = useRef<Flashcard[]>([]);

  const startSession = useCallback((cards: Flashcard[]) => {
    abortRef.current = false;
    allCardsRef.current = cards;
    failedCardsRef.current = [];
    setDueCards(cards);
    setState({ ...INITIAL_STATE, revealedPages: [] });
  }, []);

  const waitUntilReleased = useCallback(async () => {
    while (holdingRef.current) await sleep(100);
  }, []);

  const advanceToNextCard = useCallback(async () => {
    await waitUntilReleased();
    setState(prev => {
      const nextIdx = prev.currentCardIndex + 1;
      if (nextIdx >= dueCards.length) {
        return { ...prev, isSessionFinished: true, showRatingButtons: false, waitingForTap: false };
      }
      return { ...INITIAL_STATE, currentCardIndex: nextIdx, revealedPages: [] };
    });
  }, [dueCards.length, waitUntilReleased]);

  const executeStep = useCallback(async (card: Flashcard, stepIdx: number) => {
    if (abortRef.current || !group || stepIdx >= steps.length) {
      setState(s => ({ ...s, showRatingButtons: true, waitingForTap: false }));
      return;
    }
    const step = steps[stepIdx];

    switch (step.type) {
      case 'show_page':
        setState(s => ({
          ...s, currentStepIndex: stepIdx,
          revealedPages: s.revealedPages.includes(step.pageIndex)
            ? s.revealedPages : [...s.revealedPages, step.pageIndex],
        }));
        if (stepIdx === steps.length - 1) {
          setState(s => ({ ...s, waitingForTap: true }));
          return;
        }
        await executeStep(card, stepIdx + 1);
        break;

      case 'speak_page': {
        setState(s => ({ ...s, currentStepIndex: stepIdx, isTtsPlaying: true }));
        const lang = group.pageLanguages[step.pageIndex] || 'en-US';
        const text = card.pages[step.pageIndex] || '';
        const startTime = Date.now();
        try { await ttsService.speak({ text, lang }); } catch { /* ignore */ }
        lastTtsDurationRef.current = Date.now() - startTime;
        if (step.extraPauseMs > 0) await sleep(step.extraPauseMs);
        setState(s => ({ ...s, isTtsPlaying: false }));
        if (!abortRef.current) await executeStep(card, stepIdx + 1);
        break;
      }

      case 'dynamic_pause': {
        const text = card.pages[step.nextPageIndex] || '';
        const pauseMs = text.length * 60 + (step.extraPauseMs || 0);
        setState(s => ({ ...s, currentStepIndex: stepIdx }));
        await sleep(pauseMs);
        if (!abortRef.current) await executeStep(card, stepIdx + 1);
        break;
      }

      case 'wait':
        setState(s => ({ ...s, currentStepIndex: stepIdx }));
        await sleep(step.ms);
        if (!abortRef.current) await executeStep(card, stepIdx + 1);
        break;

      case 'listen_and_branch': {
        setState(s => ({ ...s, currentStepIndex: stepIdx, isSttListening: true }));
        playMicOnSound();
        const lang = group.pageLanguages[step.pageIndex] || 'en-US';
        const softTimeout = Math.max(5000, lastTtsDurationRef.current * 3);
        let recognized = '';
        try {
          recognized = await sttService.current.startListening({
            language: lang,
            timeoutMs: softTimeout + 10000,
            onPartialResult: (t) => setState(s => ({ ...s, sttResultText: t })),
            onListeningStateChange: (l) => {
              setState(s => ({ ...s, isSttListening: l }));
              if (!l) playMicOffSound();
            },
          });
        } catch { /* ignore */ }

        setState(s => ({ ...s, isSttListening: false, sttResultText: recognized || '' }));

        // Compute match
        const original = card.pages[step.pageIndex] || '';
        const percent = matchSpeech(recognized, original);
        setState(s => ({ ...s, sttMatchPercent: percent }));

        // Show result for a moment
        await sleep(1200);

        if (percent >= step.successThreshold) {
          playSuccessSound();
          const autoRating = mapMatchToRating(percent);
          await sleep(600);
          if (!abortRef.current) {
            const updated: Flashcard = { ...card, srsState: calculateFsrs(card.srsState, autoRating) };
            onCardReviewed(group.id, updated);
            await advanceToNextCard();
            return;
          }
        } else {
          playErrorSound();
          if (!failedCardsRef.current.find(c => c.id === card.id)) {
            failedCardsRef.current.push(card);
          }
        }

        if (percent < step.successThreshold) {
          if (group) {
            const allPages = group.pageNames.map((_: string, idx: number) => idx);
            setState(s => ({ ...s, revealedPages: allPages }));
          }

          if (step.incorrectTtsPageIndex !== undefined) {
            const corrLang = group.pageLanguages[step.incorrectTtsPageIndex] || 'en-US';
            setState(s => ({ ...s, isTtsPlaying: true }));
            const corrStart = Date.now();
            try { await ttsService.speak({ text: card.pages[step.incorrectTtsPageIndex] || '', lang: corrLang }); } catch { /* */ }
            const corrDuration = Date.now() - corrStart;
            setState(s => ({ ...s, isTtsPlaying: false }));
            await sleep(corrDuration * 2);
          } else {
            await sleep(2000);
          }

          if (!abortRef.current) {
            await waitUntilReleased();
            const updated: Flashcard = { ...card, srsState: calculateFsrs(card.srsState, 1) };
            onCardReviewed(group.id, updated);
            setState(prev => {
              const nextIdx = prev.currentCardIndex + 1;
              if (nextIdx >= dueCards.length) {
                return { ...prev, isSessionFinished: true, showRatingButtons: false, waitingForTap: false };
              }
              return { ...INITIAL_STATE, currentCardIndex: nextIdx, revealedPages: [] };
            });
            return;
          }
        }
        break;
      }
    }
  }, [group, steps, onCardReviewed, advanceToNextCard]);

  const handleCardTap = useCallback(() => {
    if (!state.waitingForTap || !group) return;
    const allPages = group.pageNames.map((_: string, i: number) => i);
    setState(s => ({ ...s, waitingForTap: false, revealedPages: allPages, showRatingButtons: true }));
  }, [state.waitingForTap, group]);

  const setHolding = useCallback((holding: boolean) => {
    holdingRef.current = holding;
  }, []);

  useEffect(() => {
    if (dueCards.length === 0 || state.isSessionFinished) return;
    if (state.showRatingButtons || state.waitingForTap) return;
    const card = dueCards[state.currentCardIndex];
    if (!card) return;
    executeStep(card, 0);
  }, [state.currentCardIndex, dueCards.length]);

  const handleRating = useCallback(async (rating: number) => {
    if (!group || dueCards.length === 0) return;
    const card = dueCards[state.currentCardIndex];
    if (rating === 1 && !failedCardsRef.current.find(c => c.id === card.id)) {
      failedCardsRef.current.push(card);
    }
    const updated: Flashcard = { ...card, srsState: calculateFsrs(card.srsState, rating) };
    onCardReviewed(group.id, updated);
    await advanceToNextCard();
  }, [group, dueCards, state.currentCardIndex, onCardReviewed, advanceToNextCard]);

  const restartSession = useCallback(() => {
    startSession(allCardsRef.current);
  }, [startSession]);

  const restartFailed = useCallback(() => {
    if (failedCardsRef.current.length > 0) {
      startSession([...failedCardsRef.current]);
    }
  }, [startSession]);

  const stopSession = useCallback(() => {
    abortRef.current = true;
    ttsService.cancel();
    sttService.current.stopListening();
  }, []);

  return {
    dueCards, sessionState: state, handleRating, handleCardTap,
    startSession, stopSession, setHolding, restartSession, restartFailed,
    failedCount: failedCardsRef.current.length,
  };
}

function sleep(ms: number): Promise<void> {
  return new Promise(r => setTimeout(r, ms));
}
