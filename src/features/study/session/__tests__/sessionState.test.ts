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
import { stepSummary } from '../../../settings/studyModeUtils';
import { calculateFsrs, createNewSrsState } from '../../../../srs/srsEngine';
import { recordActivityAction } from '../../../../store/actions/activityActions';
function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
  }
}

function assertArrayEqual(actual: number[], expected: number[], message: string) {
  const same = actual.length === expected.length && actual.every((v, i) => v === expected[i]);
  if (!same) {
    throw new Error(`${message}: expected [${expected.join(',')}], got [${actual.join(',')}]`);
  }
}

function assertDeepEqual(actual: unknown, expected: unknown, message: string) {
  const actualJson = JSON.stringify(actual);
  const expectedJson = JSON.stringify(expected);
  if (actualJson !== expectedJson) {
    throw new Error(`${message}: expected ${expectedJson}, got ${actualJson}`);
  }
}

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
  return {
    id,
    pages: [id],
    srsState,
  };
}

export async function runTests() {
  console.log('\n--- Running Study Session State Tests ---');

  // --- reducer ---
  const started = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, currentCardIndex: 5, status: 'revealed' },
    { type: 'START_SESSION', cards: [] },
  );
  assertEqual(started.status, 'idle', 'START_SESSION resets status');
  assertEqual(started.currentCardIndex, 0, 'START_SESSION resets card index');

  const stepKept = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0, 1], waitingForTap: true },
    { type: 'SET_CURRENT_STEP', stepIndex: 2 },
  );
  assertEqual(stepKept.currentStepIndex, 2, 'SET_CURRENT_STEP sets stepIndex');
  assertArrayEqual(stepKept.revealedPages, [0, 1], 'SET_CURRENT_STEP keeps revealedPages when omitted');
  assertEqual(stepKept.waitingForTap, true, 'SET_CURRENT_STEP keeps waitingForTap when omitted');

  const stepSet = reduce(INITIAL_STUDY_SESSION_STATE, {
    type: 'SET_CURRENT_STEP',
    stepIndex: 1,
    revealedPages: [0, 2],
    waitingForTap: true,
  });
  assertArrayEqual(stepSet.revealedPages, [0, 2], 'SET_CURRENT_STEP applies revealedPages when given');
  assertEqual(stepSet.waitingForTap, true, 'SET_CURRENT_STEP applies waitingForTap when given');

  const speaking = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'START_SPEAKING', stepIndex: 3 });
  assertEqual(speaking.status, 'speaking', 'START_SPEAKING sets speaking status');
  assertEqual(speaking.currentStepIndex, 3, 'START_SPEAKING sets stepIndex');
  assertEqual(reduce(speaking, { type: 'END_SPEAKING' }).status, 'idle', 'END_SPEAKING returns to idle');

  const listening = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, sttResultText: 'stale', sttMatchPercent: 80 },
    { type: 'START_LISTENING', stepIndex: 2 },
  );
  assertEqual(listening.status, 'listening', 'START_LISTENING sets listening status');
  assertEqual(listening.sttResultText, '', 'START_LISTENING clears partial text');
  assertEqual(listening.sttMatchPercent, 0, 'START_LISTENING clears match percent');

  const partial = reduce(listening, { type: 'UPDATE_PARTIAL_STT', text: 'hel' });
  assertEqual(partial.sttResultText, 'hel', 'UPDATE_PARTIAL_STT updates text');
  assertEqual(partial.status, 'listening', 'UPDATE_PARTIAL_STT keeps listening status');

  const checked = reduce(listening, { type: 'END_LISTENING', text: 'hello', matchPercent: 92 });
  assertEqual(checked.status, 'checking', 'END_LISTENING sets checking status');
  assertEqual(checked.sttResultText, 'hello', 'END_LISTENING stores recognized text');
  assertEqual(checked.sttMatchPercent, 92, 'END_LISTENING stores match percent');

  const revealed = reduce(INITIAL_STUDY_SESSION_STATE, {
    type: 'REVEAL_PAGES',
    revealedPages: [0, 1, 2],
  });
  assertArrayEqual(revealed.revealedPages, [0, 1, 2], 'REVEAL_PAGES sets revealedPages');

  const revealedOne = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0, 2] },
    { type: 'REVEAL_PAGE', stepIndex: 3, pageIndex: 1 },
  );
  assertArrayEqual(revealedOne.revealedPages, [0, 1, 2], 'REVEAL_PAGE adds page, dedupes and sorts');
  assertEqual(revealedOne.currentStepIndex, 3, 'REVEAL_PAGE sets stepIndex');

  const revealedDup = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0, 1] },
    { type: 'REVEAL_PAGE', stepIndex: 1, pageIndex: 1 },
  );
  assertArrayEqual(revealedDup.revealedPages, [0, 1], 'REVEAL_PAGE is idempotent for revealed page');

  const ratings = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, waitingForTap: true },
    { type: 'SHOW_RATINGS' },
  );
  assertEqual(ratings.status, 'revealed', 'SHOW_RATINGS sets revealed status');
  assertEqual(ratings.waitingForTap, false, 'SHOW_RATINGS clears waitingForTap');

  const finished = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, waitingForTap: true },
    { type: 'FINISH_SESSION' },
  );
  assertEqual(finished.status, 'finished', 'FINISH_SESSION sets finished status');
  assertEqual(finished.waitingForTap, false, 'FINISH_SESSION clears waitingForTap');

  const advanced = reduce(
    { ...INITIAL_STUDY_SESSION_STATE, currentStepIndex: 4, revealedPages: [0, 1], status: 'revealed' },
    { type: 'ADVANCE_CARD', nextCardIndex: 7 },
  );
  assertEqual(advanced.currentCardIndex, 7, 'ADVANCE_CARD sets next card index');
  assertEqual(advanced.currentStepIndex, 0, 'ADVANCE_CARD resets step index');
  assertEqual(advanced.status, 'idle', 'ADVANCE_CARD resets status');
  assertArrayEqual(advanced.revealedPages, [], 'ADVANCE_CARD resets revealedPages');

  const errored = reduce(INITIAL_STUDY_SESSION_STATE, { type: 'SET_ERROR', errorMsg: 'boom' });
  assertEqual(errored.status, 'error', 'SET_ERROR sets error status');
  assertEqual(errored.errorMsg, 'boom', 'SET_ERROR stores message');

  const cleared = reduce(errored, { type: 'CLEAR_ERROR' });
  assertEqual(cleared.status, 'idle', 'CLEAR_ERROR returns to idle');
  assertEqual(cleared.errorMsg, undefined, 'CLEAR_ERROR clears message');

  // reducer immutability: input state must not be mutated
  const original = { ...INITIAL_STUDY_SESSION_STATE, revealedPages: [0] };
  reduce(original, { type: 'REVEAL_PAGES', revealedPages: [0, 1] });
  assertArrayEqual(original.revealedPages, [0], 'reducer does not mutate input state');

  // --- utils ---
  assertArrayEqual(uniquePageIndexes([2, 0, 1, 0, 2]), [0, 1, 2], 'uniquePageIndexes dedupes and sorts');
  assertArrayEqual(uniquePageIndexes([]), [], 'uniquePageIndexes handles empty');

  const group = makeGroup(5, 3);
  assertArrayEqual(getActivePageIndexes(group), [0, 1, 2], 'getActivePageIndexes respects activePageCount');

  // page 0 is implicitly revealed; next hidden among active pages [0,1,2]
  assertEqual(getNextHiddenPageIndex(group, []), 1, 'getNextHiddenPageIndex returns first hidden (0 implicit)');
  assertEqual(getNextHiddenPageIndex(group, [1]), 2, 'getNextHiddenPageIndex skips revealed pages');
  assertEqual(getNextHiddenPageIndex(group, [1, 2]), null, 'getNextHiddenPageIndex null when all revealed');

  assertEqual(areAllActivePagesRevealed(group, [1, 2]), true, 'areAllActivePagesRevealed true when all active shown');
  assertEqual(areAllActivePagesRevealed(group, [1]), false, 'areAllActivePagesRevealed false when one hidden');
  assertEqual(
    areAllActivePagesRevealed(makeGroup(5, 1), []),
    true,
    'areAllActivePagesRevealed true for single active page (0 implicit)',
  );

  // --- stepSummary: new tap/rate step types ---
  const fakeT = ((key: string) => key) as Parameters<typeof stepSummary>[1];
  assertEqual(stepSummary({ type: 'rate' }, fakeT), 'step.rate', 'stepSummary maps rate step');
  assertEqual(
    stepSummary({ type: 'reveal_on_tap' }, fakeT),
    'step.reveal_on_tap',
    'stepSummary maps reveal_on_tap step',
  );

  // --- review attempts ---
  const reviewedAttempts = new Set<string>();
  assertEqual(getReviewAttemptKey(2, 'card-1'), '2:card-1', 'review attempt key includes attempt and id');
  let reviewAttempt = startReviewAttempt(reviewedAttempts, 0);
  assertEqual(reviewAttempt, 1, 'starting a session should create the first review attempt');
  assertEqual(
    tryMarkCardReviewed(reviewedAttempts, reviewAttempt, 'card-1'),
    true,
    'first review in a session attempt should be accepted',
  );
  assertEqual(
    tryMarkCardReviewed(reviewedAttempts, reviewAttempt, 'card-1'),
    false,
    'duplicate review in the same session attempt should be rejected',
  );
  reviewAttempt = startReviewAttempt(reviewedAttempts, reviewAttempt);
  assertEqual(
    tryMarkCardReviewed(reviewedAttempts, reviewAttempt, 'card-1'),
    true,
    'same card should be reviewable again after restartFailed starts a new session attempt',
  );

  let reviewedCard = makeCard('card-2');
  let reviewHeatmap: Record<string, number> = {};
  const applyTrackedReview = (attempt: number) => {
    if (!tryMarkCardReviewed(reviewedAttempts, attempt, reviewedCard.id)) {
      return false;
    }

    reviewedCard = { ...reviewedCard, srsState: calculateFsrs(reviewedCard.srsState, 1) };
    const activity = recordActivityAction(reviewHeatmap);
    reviewHeatmap = activity.nextHeatmap;
    return activity.todayKey;
  };

  reviewAttempt = startReviewAttempt(reviewedAttempts, reviewAttempt);
  const firstReviewDay = applyTrackedReview(reviewAttempt);
  assertEqual(typeof firstReviewDay, 'string', 'first failed-card review should update FSRS and heatmap');
  assertEqual(reviewedCard.srsState.repetitions, 1, 'first failed-card review should update FSRS repetitions');
  assertEqual(
    applyTrackedReview(reviewAttempt),
    false,
    'duplicate failed-card review in one attempt should not update again',
  );
  reviewAttempt = startReviewAttempt(reviewedAttempts, reviewAttempt);
  const secondReviewDay = applyTrackedReview(reviewAttempt);
  assertEqual(secondReviewDay, firstReviewDay, 'restartFailed review should record activity on the same day');
  assertEqual(reviewedCard.srsState.repetitions, 2, 'restartFailed review should update FSRS again');
  assertEqual(
    reviewHeatmap[firstReviewDay as string],
    2,
    'restartFailed review should increment heatmap again',
  );

  // --- session progress ---
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
  assertDeepEqual(
    buildSessionProgressItems(staleDueCards, liveCards),
    [
      { id: 'a', category: 'review' },
      { id: 'b', category: 'new' },
      { id: 'c', category: 'mastered' },
    ],
    'session progress should use live group card categories instead of stale due cards',
  );
  assertDeepEqual(
    getSessionProgressSegments(
      [
        { id: 'a', category: 'review' },
        { id: 'b', category: 'new' },
        { id: 'c', category: 'mastered' },
      ],
      1,
    ),
    [
      { id: 'a', category: 'review', state: 'past' },
      { id: 'b', category: 'new', state: 'current' },
      { id: 'c', category: 'mastered', state: 'future' },
    ],
    'session progress segments should mark past, current, and future cards correctly',
  );

  console.log('Study session state tests passed');
}
