import { describe, it, expect } from '@jest/globals';

import type { Flashcard, FlashcardGroup } from '../../../../types/models';
import { INITIAL_STUDY_SESSION_STATE, sessionReducer } from '../sessionReducer';
import type { SessionAction, StudySessionState } from '../sessionTypes';
import {
  areAllActivePagesRevealed,
  getActivePageIndexes,
  getNextHiddenPageIndex,
  uniquePageIndexes,
} from '../sessionUtils';
import {
  buildSessionProgressItems,
  getSessionProgressSegments,
} from '../sessionProgress';
import {
  getReviewAttemptKey,
  startReviewAttempt,
  tryMarkCardReviewed,
} from '../sessionReview';
import { formatStepSummary } from '../../../settings/studyModeUtils';
import { calculateFsrs, createNewSrsState } from '../../../../srs/srsEngine';
import { recordActivityAction } from '../../../../store/actions/cardActions';

function makeGroup(pageCount: number, activePageCount: number): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test',
    cards: [],
    activeModeId: '',
    studyFilter: 'all',
    pageLanguages: Array.from({ length: pageCount }, () => 'en-US'),
    pageNames: Array.from({ length: pageCount }, (_, i) => `Page ${i + 1}`),
    activePageCount,
  };
}

function reduce(state: StudySessionState, action: SessionAction): StudySessionState {
  return sessionReducer(state, action);
}

function makeCard(id: string, srsState = createNewSrsState()): Flashcard {
  return { id, pages: [id], srsState };
}

describe('sessionReducer', () => {
  describe('START_SESSION', () => {
    it('resets status and card index', () => {
      const started = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, currentCardIndex: 5, status: 'revealed' },
        { type: 'START_SESSION' },
      );
      expect(started.status).toBe('idle');
      expect(started.currentCardIndex).toBe(0);
    });
  });

  describe('SET_CURRENT_STEP', () => {
    it('sets stepIndex and keeps revealedPages / waitingForTap when omitted', () => {
      const stepKept = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0, 1], waitingForTap: true },
        { type: 'SET_CURRENT_STEP', stepIndex: 2 },
      );
      expect(stepKept.currentStepIndex).toBe(2);
      expect(stepKept.revealedPages).toEqual([0, 1]);
      expect(stepKept.waitingForTap).toBe(true);
    });

    it('applies revealedPages and waitingForTap when given', () => {
      const stepSet = reduce(INITIAL_STUDY_SESSION_STATE, {
        type: 'SET_CURRENT_STEP',
        stepIndex: 1,
        revealedPages: [0, 2],
        waitingForTap: true,
      });
      expect(stepSet.revealedPages).toEqual([0, 2]);
      expect(stepSet.waitingForTap).toBe(true);
    });
  });

  describe('START_SPEAKING / END_SPEAKING', () => {
    it('sets speaking status, stepIndex and audio page, END_SPEAKING returns to idle', () => {
      const speaking = reduce(INITIAL_STUDY_SESSION_STATE, {
        type: 'START_SPEAKING',
        stepIndex: 3,
        pageIndex: 1,
      });
      expect(speaking.status).toBe('speaking');
      expect(speaking.currentStepIndex).toBe(3);
      expect(speaking.audioPageIndex).toBe(1);
      const ended = reduce(speaking, { type: 'END_SPEAKING' });
      expect(ended.status).toBe('idle');
      expect(ended.audioPageIndex).toBeNull();
    });
  });

  describe('START_LISTENING', () => {
    it('sets listening status, audio page and clears partial text / match percent', () => {
      const listening = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, sttResultText: 'stale', sttMatchPercent: 80 },
        { type: 'START_LISTENING', stepIndex: 2, pageIndex: 0 },
      );
      expect(listening.status).toBe('listening');
      expect(listening.audioPageIndex).toBe(0);
      expect(listening.sttResultText).toBe('');
      expect(listening.sttMatchPercent).toBe(0);
    });
  });

  describe('UPDATE_PARTIAL_STT', () => {
    it('updates text while keeping listening status', () => {
      const listening = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'START_LISTENING', stepIndex: 2, pageIndex: 0 });
      const partial = reduce(listening, { type: 'UPDATE_PARTIAL_STT', text: 'hel' });
      expect(partial.sttResultText).toBe('hel');
      expect(partial.status).toBe('listening');
    });

    it('ignores late partials once no longer listening (post-skip/advance zombie callback)', () => {
      const revealed = { ...INITIAL_STUDY_SESSION_STATE, status: 'revealed' as const, sttResultText: 'final' };
      const partial = reduce(revealed, { type: 'UPDATE_PARTIAL_STT', text: 'stale partial' });
      expect(partial).toBe(revealed); // unchanged reference
      expect(partial.sttResultText).toBe('final');
    });
  });

  describe('END_LISTENING', () => {
    it('sets checking status, clears audio page and stores text / match percent', () => {
      const listening = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'START_LISTENING', stepIndex: 2, pageIndex: 0 });
      const checked = reduce(listening, { type: 'END_LISTENING', text: 'hello', matchPercent: 92 });
      expect(checked.status).toBe('checking');
      expect(checked.audioPageIndex).toBeNull();
      expect(checked.sttResultText).toBe('hello');
      expect(checked.sttMatchPercent).toBe(92);
    });
  });

  describe('REVEAL_PAGES', () => {
    it('sets revealedPages', () => {
      const revealed = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'REVEAL_PAGES', revealedPages: [0, 1, 2] });
      expect(revealed.revealedPages).toEqual([0, 1, 2]);
    });
  });

  describe('REVEAL_PAGE', () => {
    it('adds page, dedupes and sorts', () => {
      const revealedOne = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0, 2] },
        { type: 'REVEAL_PAGE', stepIndex: 3, pageIndex: 1 },
      );
      expect(revealedOne.revealedPages).toEqual([0, 1, 2]);
      expect(revealedOne.currentStepIndex).toBe(3);
    });

    it('is idempotent for already revealed page', () => {
      const revealedDup = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0, 1] },
        { type: 'REVEAL_PAGE', stepIndex: 1, pageIndex: 1 },
      );
      expect(revealedDup.revealedPages).toEqual([0, 1]);
    });
  });

  describe('SHOW_RATINGS', () => {
    it('sets revealed status and clears waitingForTap', () => {
      const ratings = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, waitingForTap: true },
        { type: 'SHOW_RATINGS' },
      );
      expect(ratings.status).toBe('revealed');
      expect(ratings.waitingForTap).toBe(false);
    });
  });

  describe('FINISH_SESSION', () => {
    it('sets finished status and clears waitingForTap', () => {
      const finished = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, waitingForTap: true },
        { type: 'FINISH_SESSION' },
      );
      expect(finished.status).toBe('finished');
      expect(finished.waitingForTap).toBe(false);
    });

    it('forces finished from an in-progress status (early end mid-step)', () => {
      const finished = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, status: 'speaking', currentCardIndex: 2, waitingForTap: true },
        { type: 'FINISH_SESSION' },
      );
      expect(finished.status).toBe('finished');
      expect(finished.waitingForTap).toBe(false);
      // Position is preserved so the summary reflects where the user stopped.
      expect(finished.currentCardIndex).toBe(2);
    });
  });

  describe('ADVANCE_CARD', () => {
    it('sets next card index, resets step index, status, and revealedPages', () => {
      const advanced = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, currentStepIndex: 4, revealedPages: [0, 1], status: 'revealed' },
        { type: 'ADVANCE_CARD', nextCardIndex: 7 },
      );
      expect(advanced.currentCardIndex).toBe(7);
      expect(advanced.currentStepIndex).toBe(0);
      expect(advanced.status).toBe('idle');
      expect(advanced.revealedPages).toEqual([]);
    });
  });

  describe('SET_ERROR / CLEAR_ERROR', () => {
    it('sets error status and message', () => {
      const errored = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'SET_ERROR', errorMsg: 'boom' });
      expect(errored.status).toBe('error');
      expect(errored.errorMsg).toBe('boom');
    });

    it('clears message', () => {
      const errored = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'SET_ERROR', errorMsg: 'boom' });
      const cleared = reduce(errored, { type: 'CLEAR_ERROR' });
      expect(cleared.errorMsg).toBeUndefined();
    });

    it('preserves active status on CLEAR_ERROR', () => {
      const clearedWhileRevealed = reduce(
        { ...INITIAL_STUDY_SESSION_STATE, status: 'revealed', errorMsg: 'study.error.stt' },
        { type: 'CLEAR_ERROR' },
      );
      expect(clearedWhileRevealed.status).toBe('revealed');
      expect(clearedWhileRevealed.errorMsg).toBeUndefined();
    });
  });

  describe('immutability', () => {
    it('does not mutate input state', () => {
      const original = { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0] };
      reduce(original, { type: 'REVEAL_PAGES', revealedPages: [0, 1] });
      expect(original.revealedPages).toEqual([0]);
    });
  });
});

describe('peek', () => {
  function reducePeek(state: StudySessionState, action: SessionAction): StudySessionState {
    return sessionReducer(state, action);
  }

  it('PEEK_SET sets peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    expect(peeked.peekedPageIndex).toBe(2);
  });

  it('PEEK_SET replaces peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const replaced = reducePeek(peeked, { type: 'PEEK_SET', pageIndex: 3 });
    expect(replaced.peekedPageIndex).toBe(3);
  });

  it('PEEK_CLEAR clears peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const cleared = reducePeek(peeked, { type: 'PEEK_CLEAR' });
    expect(cleared.peekedPageIndex).toBeNull();
  });

  it('SET_CURRENT_STEP clears peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const cleared = reducePeek(peeked, { type: 'SET_CURRENT_STEP', stepIndex: 5 });
    expect(cleared.peekedPageIndex).toBeNull();
  });

  it('REVEAL_PAGE does not change peek for unrelated page', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const revealed = reducePeek(peeked, { type: 'REVEAL_PAGE', stepIndex: 1, pageIndex: 1 });
    expect(revealed.peekedPageIndex).toBe(2);
  });

  it('REVEAL_PAGE clears peek when peeked page is revealed', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const revealed = reducePeek(peeked, { type: 'REVEAL_PAGE', stepIndex: 1, pageIndex: 2 });
    expect(revealed.peekedPageIndex).toBeNull();
  });

  it('REVEAL_PAGES clears peek when peeked page is included', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const revealed = reducePeek(peeked, { type: 'REVEAL_PAGES', revealedPages: [0, 1, 2] });
    expect(revealed.peekedPageIndex).toBeNull();
  });

  it('ADVANCE_CARD resets peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const advanced = reducePeek(peeked, { type: 'ADVANCE_CARD', nextCardIndex: 1 });
    expect(advanced.peekedPageIndex).toBeNull();
  });

  it('SHOW_RATINGS clears peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const ratings = reducePeek(peeked, { type: 'SHOW_RATINGS' });
    expect(ratings.peekedPageIndex).toBeNull();
  });

  it('FINISH_SESSION clears peekedPageIndex', () => {
    const peeked = reducePeek(INITIAL_STUDY_SESSION_STATE, { type: 'PEEK_SET', pageIndex: 2 });
    const finished = reducePeek(peeked, { type: 'FINISH_SESSION' });
    expect(finished.peekedPageIndex).toBeNull();
  });
});

describe('sessionUtils', () => {
  it('uniquePageIndexes dedupes and sorts', () => {
    expect(uniquePageIndexes([2, 0, 1, 0, 2])).toEqual([0, 1, 2]);
  });

  it('uniquePageIndexes handles empty', () => {
    expect(uniquePageIndexes([])).toEqual([]);
  });

  it('getActivePageIndexes respects activePageCount', () => {
    const group = makeGroup(5, 3);
    expect(getActivePageIndexes(group)).toEqual([0, 1, 2]);
  });

  it('getNextHiddenPageIndex returns first hidden (0 implicit)', () => {
    const group = makeGroup(5, 3);
    expect(getNextHiddenPageIndex(group, [])).toBe(1);
  });

  it('getNextHiddenPageIndex skips revealed pages', () => {
    const group = makeGroup(5, 3);
    expect(getNextHiddenPageIndex(group, [1])).toBe(2);
  });

  it('getNextHiddenPageIndex returns null when all revealed', () => {
    const group = makeGroup(5, 3);
    expect(getNextHiddenPageIndex(group, [1, 2])).toBeNull();
  });

  it('areAllActivePagesRevealed returns true when all active shown', () => {
    const group = makeGroup(5, 3);
    expect(areAllActivePagesRevealed(group, [1, 2])).toBe(true);
  });

  it('areAllActivePagesRevealed returns false when one hidden', () => {
    const group = makeGroup(5, 3);
    expect(areAllActivePagesRevealed(group, [1])).toBe(false);
  });

  it('areAllActivePagesRevealed returns true for single active page (0 implicit)', () => {
    expect(areAllActivePagesRevealed(makeGroup(5, 1), [])).toBe(true);
  });
});

describe('formatStepSummary', () => {
  const fakeT = ((key: string) => key) as Parameters<typeof formatStepSummary>[1];

  it('maps rate step', () => {
    expect(formatStepSummary({ type: 'rate' }, fakeT)).toBe('step.rate');
  });
});

describe('reviewAttempts', () => {
  it('getReviewAttemptKey includes attempt and id', () => {
    expect(getReviewAttemptKey(2, 'card-1')).toBe('2:card-1');
  });

  it('startReviewAttempt creates the first review attempt', () => {
    const reviewedAttempts = new Set<string>();
    expect(startReviewAttempt(reviewedAttempts, 0)).toBe(1);
  });

  it('tryMarkCardReviewed accepts first review in a session attempt', () => {
    const reviewedAttempts = new Set<string>();
    const attempt = startReviewAttempt(reviewedAttempts, 0);
    expect(tryMarkCardReviewed(reviewedAttempts, attempt, 'card-1')).toBe(true);
  });

  it('tryMarkCardReviewed rejects duplicate review in same attempt', () => {
    const reviewedAttempts = new Set<string>();
    const attempt = startReviewAttempt(reviewedAttempts, 0);
    tryMarkCardReviewed(reviewedAttempts, attempt, 'card-1');
    expect(tryMarkCardReviewed(reviewedAttempts, attempt, 'card-1')).toBe(false);
  });

  it('same card is reviewable again after new attempt', () => {
    const reviewedAttempts = new Set<string>();
    let attempt = startReviewAttempt(reviewedAttempts, 0);
    tryMarkCardReviewed(reviewedAttempts, attempt, 'card-1');
    attempt = startReviewAttempt(reviewedAttempts, attempt);
    expect(tryMarkCardReviewed(reviewedAttempts, attempt, 'card-1')).toBe(true);
  });

  it('first failed-card review updates FSRS and heatmap', () => {
    const reviewedAttempts = new Set<string>();
    let reviewedCard = makeCard('card-2');
    let reviewHeatmap: Record<string, number> = {};
    let attempt = startReviewAttempt(reviewedAttempts, 0);

    const applyTrackedReview = (att: number) => {
      if (!tryMarkCardReviewed(reviewedAttempts, att, reviewedCard.id)) return false;
      reviewedCard = { ...reviewedCard, srsState: calculateFsrs(reviewedCard.srsState, 1) };
      const activity = recordActivityAction(reviewHeatmap);
      reviewHeatmap = activity.nextHeatmap;
      return activity.todayKey;
    };

    attempt = startReviewAttempt(reviewedAttempts, attempt);
    const firstReviewDay = applyTrackedReview(attempt);
    expect(typeof firstReviewDay).toBe('string');
    expect(reviewedCard.srsState.repetitions).toBe(1);
    expect(applyTrackedReview(attempt)).toBe(false);

    attempt = startReviewAttempt(reviewedAttempts, attempt);
    const secondReviewDay = applyTrackedReview(attempt);
    expect(secondReviewDay).toBe(firstReviewDay);
    expect(reviewedCard.srsState.repetitions).toBe(2);
    expect(reviewHeatmap[firstReviewDay as string]).toBe(2);
  });
});

describe('sessionProgress', () => {
  it('uses live group card categories instead of stale due cards', () => {
    const liveCards = [
      makeCard('a', { ...createNewSrsState(), state: 2, repetitions: 1, stability: 1 }),
      makeCard('b', { ...createNewSrsState(), state: 0 }),
      makeCard('c', { ...createNewSrsState(), state: 2, repetitions: 5, stability: 1 }),
    ];
    const staleDueCards = [
      makeCard('a', { ...createNewSrsState(), state: 0 }),
      makeCard('b', { ...createNewSrsState(), state: 0 }),
      makeCard('c', { ...createNewSrsState(), state: 0 }),
    ];
    expect(buildSessionProgressItems(staleDueCards, liveCards)).toEqual([
      { id: 'a', category: 'review' },
      { id: 'b', category: 'new' },
      { id: 'c', category: 'mastered' },
    ]);
  });

  it('marks past, current, and future cards correctly', () => {
    expect(
      getSessionProgressSegments(
        [
          { id: 'a', category: 'review' },
          { id: 'b', category: 'new' },
          { id: 'c', category: 'mastered' },
        ],
        1,
      ),
    ).toEqual([
      { id: 'a', category: 'review', state: 'past' },
      { id: 'b', category: 'new', state: 'current' },
      { id: 'c', category: 'mastered', state: 'future' },
    ]);
  });
});
