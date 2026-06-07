import { useCallback, useRef, useState } from 'react';
import type { MutableRefObject } from 'react';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import {
  startReviewAttempt,
  tryMarkCardReviewed,
} from '@/features/study/session/sessionReview';
import { sleep } from '@/features/study/session/sessionUtils';
import { useSyncedRef } from '@/hooks/useSyncedRef';
import { playRatingHaptic } from '@/services/hapticFeedback';
import type {
  SessionAction,
  StudySessionState,
} from '@/features/study/session/sessionTypes';

interface UseStudyReviewFlowParams {
  groupRef: MutableRefObject<FlashcardGroup | null>;
  stateRef: MutableRefObject<StudySessionState>;
  holdingRef: MutableRefObject<boolean>;
  onCardReviewedRef: MutableRefObject<(groupId: string, cardId: string, rating: number) => void>;
  dispatchIfMounted: (action: SessionAction) => void;
  isMountedRef: MutableRefObject<boolean>;
  prepareStartSession: () => void;
}

const WAIT_RELEASE_POLL_MS = 100;

export function useStudyReviewFlow({
  groupRef,
  stateRef,
  holdingRef,
  onCardReviewedRef,
  dispatchIfMounted,
  isMountedRef,
  prepareStartSession,
}: UseStudyReviewFlowParams) {
  const [dueCards, setDueCards] = useState<Flashcard[]>([]);
  const [failedCount, setFailedCount] = useState(0);
  const allCardsRef = useRef<Flashcard[]>([]);
  const failedCardsRef = useRef<Flashcard[]>([]);
  const reviewedAttemptKeysRef = useRef<Set<string>>(new Set());
  const sessionAttemptRef = useRef(0);
  const dueCardsRef = useSyncedRef(dueCards);

  const syncFailedCount = useCallback(() => {
    if (isMountedRef.current) {
      setFailedCount(failedCardsRef.current.length);
    }
  }, [isMountedRef]);

  const markCardFailed = useCallback(
    (card: Flashcard) => {
      if (failedCardsRef.current.find((item) => item.id === card.id)) return;
      failedCardsRef.current.push(card);
      syncFailedCount();
    },
    [syncFailedCount],
  );

  const unmarkCardFailed = useCallback(
    (cardId: string) => {
      const previousLength = failedCardsRef.current.length;
      failedCardsRef.current = failedCardsRef.current.filter((card) => card.id !== cardId);
      if (failedCardsRef.current.length !== previousLength) {
        syncFailedCount();
      }
    },
    [syncFailedCount],
  );

  const startSession = useCallback(
    (cards: Flashcard[]) => {
      prepareStartSession();
      allCardsRef.current = cards;
      failedCardsRef.current = [];
      setFailedCount(0);
      sessionAttemptRef.current = startReviewAttempt(
        reviewedAttemptKeysRef.current,
        sessionAttemptRef.current,
      );
      setDueCards(cards);
      dispatchIfMounted({ type: 'START_SESSION' });
    },
    [dispatchIfMounted, prepareStartSession],
  );

  const processCardReview = useCallback(
    (card: Flashcard, rating: number) => {
      const currentGroup = groupRef.current;
      if (!currentGroup) return;
      if (
        tryMarkCardReviewed(reviewedAttemptKeysRef.current, sessionAttemptRef.current, card.id)
      ) {
        // Pass id + rating only; the store recomputes FSRS on the live card so a
        // stale snapshot can't overwrite edits/deletes made during the session.
        onCardReviewedRef.current(currentGroup.id, card.id, rating);
      }
    },
    [groupRef, onCardReviewedRef],
  );

  const waitUntilReleased = useCallback(async () => {
    while (holdingRef.current) {
      await sleep(WAIT_RELEASE_POLL_MS);
    }
  }, [holdingRef]);

  const advanceToNextCard = useCallback(async () => {
    await waitUntilReleased();
    const nextIndex = stateRef.current.currentCardIndex + 1;
    if (nextIndex >= dueCardsRef.current.length) {
      dispatchIfMounted({ type: 'FINISH_SESSION' });
    } else {
      dispatchIfMounted({ type: 'ADVANCE_CARD', nextCardIndex: nextIndex });
    }
  }, [dispatchIfMounted, dueCardsRef, stateRef, waitUntilReleased]);

  const handleRating = useCallback(
    async (rating: number) => {
      const index = stateRef.current.currentCardIndex;
      if (index >= dueCardsRef.current.length || !groupRef.current) return;
      const card = dueCardsRef.current[index];
      if (!card) return;
      playRatingHaptic(rating);
      if (rating === 1) {
        markCardFailed(card);
      }
      processCardReview(card, rating);
      await advanceToNextCard();
    },
    [
      advanceToNextCard,
      dueCardsRef,
      groupRef,
      markCardFailed,
      processCardReview,
      stateRef,
    ],
  );

  const getFreshCards = useCallback(
    (cards: Flashcard[]) => {
      const currentGroup = groupRef.current;
      if (!currentGroup) return cards;

      const cardsById = new Map(currentGroup.cards.map((card) => [card.id, card]));
      return cards.map((card) => cardsById.get(card.id) ?? card);
    },
    [groupRef],
  );

  const restartSession = useCallback(() => {
    startSession(getFreshCards(allCardsRef.current));
  }, [getFreshCards, startSession]);

  const restartFailed = useCallback(() => {
    if (failedCardsRef.current.length > 0) {
      startSession(getFreshCards(failedCardsRef.current));
    }
  }, [getFreshCards, startSession]);

  return {
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
  };
}
