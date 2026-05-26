import {
  createNewSrsState,
  calculateFsrs,
  matchSpeech,
  mapMatchToRating,
  getCardCategory,
} from '../srsEngine';
import {
  parseCSVLine,
  detectSeparator,
  detectPageCount,
  detectLangFromHeader,
  parseCSV,
} from '../../import/importParser';
import {
  getVisiblePages,
  getVisiblePageNames,
  getVisiblePageLanguages,
  ensureCardHasPageSlots,
  normalizeGroupPageConfig,
} from '../../store/selectors/pages';
import { filterCards } from '../../store/selectors/dueCards';
import { validateBackupData } from '../../utils/backupValidation';
import { validateImportDeckPayload } from '../../import/importDeck';
import type { Flashcard, FlashcardGroup } from '../../types/models';

console.log('Running RayColl Spaced Repetition Engine and Parser tests...');

function assertEqual<T>(actual: T, expected: T, msg?: string) {
  if (actual !== expected) {
    throw new Error(
      `Assertion failed: expected ${expected}, got ${actual}${msg ? ` - ${msg}` : ''}`,
    );
  }
}

function assertDeepEqual<T>(actual: T, expected: T, msg?: string) {
  const aStr = JSON.stringify(actual);
  const eStr = JSON.stringify(expected);
  if (aStr !== eStr) {
    throw new Error(`Assertion failed: expected ${eStr}, got ${aStr}${msg ? ` - ${msg}` : ''}`);
  }
}

function assertOk(value: unknown, msg?: string) {
  if (!value) {
    throw new Error(
      `Assertion failed: expected truthy value, got ${value}${msg ? ` - ${msg}` : ''}`,
    );
  }
}

function assertThrows(fn: () => void, expectedMessagePart?: string) {
  try {
    fn();
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    if (expectedMessagePart && !message.includes(expectedMessagePart)) {
      throw new Error(
        `Expected error containing "${expectedMessagePart}", but got: "${message}"`,
      );
    }
    return;
  }
  throw new Error('Expected function to throw an error, but it did not.');
}

// ==========================================
// 1. Spaced Repetition (FSRS Engine) Tests
// ==========================================
console.log('\n--- Running FSRS Engine Tests ---');

const state = createNewSrsState();
assertEqual(state.difficulty, 5, 'Initial difficulty should be 5');
assertEqual(state.stability, 0, 'Initial stability should be 0');
assertEqual(state.repetitions, 0, 'Initial repetitions should be 0');
assertEqual(state.state, 0, 'Initial state should be 0 (New)');
console.log('✓ createNewSrsState test passed');

// Card categories mapping
assertEqual(getCardCategory(state), 'new', 'Category should be "new"');
const learningState = { ...state, state: 1 };
assertEqual(getCardCategory(learningState), 'learning', 'Category should be "learning"');
const relearningState = { ...state, state: 3 };
assertEqual(
  getCardCategory(relearningState),
  'learning',
  'Category should be "learning" for relearning state',
);
const reviewState = { ...state, state: 2, repetitions: 2 };
assertEqual(getCardCategory(reviewState), 'review', 'Category should be "review"');
const masteredState = { ...state, state: 2, repetitions: 3 };
assertEqual(getCardCategory(masteredState), 'mastered', 'Category should be "mastered"');
console.log('✓ getCardCategory tests passed');

// Match speech tests
assertEqual(
  matchSpeech('hello world', 'Hello World!'),
  100,
  'Exact match case/punctuation insensitive',
);
assertEqual(matchSpeech('hello', 'hello world'), 50, 'Partial match');
assertEqual(matchSpeech('', ''), 100, 'Empty match');
console.log('✓ matchSpeech tests passed');

// Match to rating map
assertEqual(mapMatchToRating(90), 4, '90% -> 4 (Easy)');
assertEqual(mapMatchToRating(70), 3, '70% -> 3 (Good)');
assertEqual(mapMatchToRating(50), 2, '50% -> 2 (Hard)');
assertEqual(mapMatchToRating(20), 1, '20% -> 1 (Again)');
console.log('✓ mapMatchToRating tests passed');

// Spaced Repetition calculation
const firstReview = calculateFsrs(state, 3); // Good rating
assertEqual(firstReview.repetitions, 1, 'Repetitions should increment');
assertOk(firstReview.stability > 0, 'Stability should increase');
assertOk(firstReview.difficulty > 0, 'Difficulty should be calculated');
assertEqual(firstReview.state, 2, 'State should change to 2 (Review)');
console.log('✓ calculateFsrs tests passed');

// ==========================================
// 2. CSV / TSV Import Parser Tests
// ==========================================
console.log('\n--- Running CSV Parser Tests ---');

// parseCSVLine
assertDeepEqual(parseCSVLine('hello;world', ';'), ['hello', 'world']);
assertDeepEqual(parseCSVLine('"hello;nested";world', ';'), ['hello;nested', 'world']);
assertDeepEqual(parseCSVLine('hello\tworld', '\t'), ['hello', 'world']);
console.log('✓ parseCSVLine tests passed');

// detectSeparator
assertEqual(detectSeparator('hello;world;example'), 'semicolon');
assertEqual(detectSeparator('hello,world,example'), 'comma');
assertEqual(detectSeparator('hello\tworld\texample'), 'tab');
assertEqual(detectSeparator('hello|world|example'), 'pipe');
console.log('✓ detectSeparator tests passed');

// detectPageCount
assertEqual(detectPageCount('hello;world;example\none;two;three', ';'), 3);
assertEqual(detectPageCount('hello;world\none;two;three', ';'), 3); // max count
console.log('✓ detectPageCount tests passed');

// detectLangFromHeader
assertEqual(detectLangFromHeader('English'), 'en-US');
assertEqual(detectLangFromHeader('słowo'), 'pl-PL');
assertEqual(detectLangFromHeader('palabra'), 'es-ES');
assertEqual(detectLangFromHeader('Wort'), 'de-DE');
assertEqual(detectLangFromHeader('french'), 'fr-FR');
assertEqual(detectLangFromHeader('italiano'), 'it-IT');
console.log('✓ detectLangFromHeader tests passed');

// parseCSV
assertDeepEqual(parseCSV('hello;world\nfoo;bar', 'semicolon', 2), [
  ['hello', 'world'],
  ['foo', 'bar'],
]);
assertDeepEqual(
  parseCSV('hello;world\nfoo', 'semicolon', 2),
  [
    ['hello', 'world'],
    ['foo', ''],
  ], // pad with empty slot
);
console.log('✓ parseCSV tests passed');

// ==========================================
// 3. Page Config & Selectors Tests
// ==========================================
console.log('\n--- Running Page Config and Selector Tests ---');

const mockGroup: FlashcardGroup = {
  id: 'g1',
  name: 'Mock Deck',
  cards: [],
  activeModeId: 'classic',
  pageNames: ['Front', 'Back', 'Example'],
  pageLanguages: ['en-US', 'pl-PL', 'en-US'],
  activePageCount: 2,
};

const mockCard: Flashcard = {
  id: 'c1',
  pages: ['hello', 'cześć', 'hello world'],
  srsState: createNewSrsState(),
};

// getVisiblePages
assertDeepEqual(
  getVisiblePages(mockCard, mockGroup),
  ['hello', 'cześć'],
  'Should slice card pages to activePageCount',
);

const shortCard: Flashcard = {
  id: 'c2',
  pages: ['hello'],
  srsState: createNewSrsState(),
};
assertDeepEqual(
  getVisiblePages(shortCard, mockGroup),
  ['hello', ''],
  'Should pad visible pages to activePageCount if card lacks slots',
);

// getVisiblePageNames & Languages
assertDeepEqual(
  getVisiblePageNames(mockGroup),
  ['Front', 'Back'],
  'Should slice pageNames to activePageCount',
);
assertDeepEqual(
  getVisiblePageLanguages(mockGroup),
  ['en-US', 'pl-PL'],
  'Should slice pageLanguages to activePageCount',
);

// ensureCardHasPageSlots
const paddedCard = ensureCardHasPageSlots(shortCard, 3);
assertDeepEqual(paddedCard.pages, ['hello', '', ''], 'Should pad card pages to minPageCount');

// normalizeGroupPageConfig
const unnormalizedGroup: FlashcardGroup = {
  id: 'g2',
  name: 'Unnormalized',
  cards: [],
  activeModeId: 'classic',
  pageNames: ['Front'],
  pageLanguages: [],
  activePageCount: 3,
};
const normalized = normalizeGroupPageConfig(unnormalizedGroup);
assertEqual(normalized.pageNames.length, 3);
assertEqual(normalized.pageLanguages.length, 3);
assertEqual(normalized.pageNames[1], 'Page 2');
assertEqual(normalized.pageLanguages[1], 'en-US');
console.log('✓ Page config and selector tests passed');

// ==========================================
// 4. Verification of Pages Model Bounds (No Data Loss)
// ==========================================
console.log('\n--- Running Page Count Modification / Data Loss Prevention Tests ---');

const myCard: Flashcard = {
  id: 'c_test',
  pages: ['Word', 'Tłumaczenie', 'Hidden Example Note'],
  srsState: createNewSrsState(),
};

const myGroup: FlashcardGroup = {
  id: 'g_test',
  name: 'Test Deck',
  cards: [myCard],
  activeModeId: 'classic',
  pageNames: ['Front', 'Back', 'Example'],
  pageLanguages: ['en-US', 'pl-PL', 'en-US'],
  activePageCount: 3,
};

// 1. Initial State: activePageCount = 3
assertDeepEqual(getVisiblePages(myCard, myGroup), ['Word', 'Tłumaczenie', 'Hidden Example Note']);

// 2. Decrease activePageCount to 2
const groupReduced = { ...myGroup, activePageCount: 2 };
assertDeepEqual(
  getVisiblePages(myCard, groupReduced),
  ['Word', 'Tłumaczenie'],
  'Visible pages should be sliced to 2',
);
assertDeepEqual(
  myCard.pages,
  ['Word', 'Tłumaczenie', 'Hidden Example Note'],
  'Physical card.pages array MUST NOT be truncated!',
);

// 3. Increase activePageCount back to 3
const groupRestored = { ...myGroup, activePageCount: 3 };
assertDeepEqual(
  getVisiblePages(myCard, groupRestored),
  ['Word', 'Tłumaczenie', 'Hidden Example Note'],
  'Hidden pages must be fully recovered!',
);
console.log('✓ Page reduction / enlargement data loss prevention tests passed');

// ==========================================
// 5. Card Filtering Tests
// ==========================================
console.log('\n--- Running Card Filter Tests ---');

const now = Date.now();
const newCard: Flashcard = {
  id: 'c_new',
  pages: ['New', 'Nowy'],
  srsState: { ...createNewSrsState(), state: 0 },
};
const dueCard: Flashcard = {
  id: 'c_due',
  pages: ['Due', 'Należny'],
  srsState: { ...createNewSrsState(), state: 2, nextReviewTimestamp: now - 1000 },
};
const notDueCard: Flashcard = {
  id: 'c_not_due',
  pages: ['Not Due', 'Nienależny'],
  srsState: { ...createNewSrsState(), state: 2, nextReviewTimestamp: now + 500000 },
};

const allCards = [newCard, dueCard, notDueCard];

// filter = 'all'
assertEqual(filterCards(allCards, 'all').length, 3);
// filter = 'new'
assertDeepEqual(filterCards(allCards, 'new'), [newCard]);
// filter = 'review'
assertDeepEqual(filterCards(allCards, 'review'), [dueCard]);
// filter = 'new+review'
assertDeepEqual(filterCards(allCards, 'new+review'), [newCard, dueCard]);
console.log('✓ Card filtering tests passed');

// ==========================================
// 6. Backup Validation Tests
// ==========================================
console.log('\n--- Running Backup Validation Tests ---');

const validBackup = {
  groups: [
    {
      id: 'g_backup',
      name: 'Backup Group',
      cards: [
        {
          id: 'c_backup',
          pages: ['A', 'B'],
          srsState: createNewSrsState(),
        },
      ],
      activeModeId: 'classic',
      pageLanguages: ['en-US', 'pl-PL'],
      pageNames: ['Front', 'Back'],
    },
  ],
  studyModes: [
    {
      id: 'classic',
      name: 'Classic',
      steps: [{ type: 'show_page', pageIndex: 0 }],
    },
  ],
  activityHeatmap: { '2026-05-26': 1 },
};

// Should not throw on valid backup
assertOk(validateBackupData(validBackup));

// Should throw on invalid backups
assertThrows(() => validateBackupData(null), 'Backup data is not a valid JSON object.');
assertThrows(() => validateBackupData({}), 'Backup data must contain a "groups" array.');
assertThrows(
  () => validateBackupData({ groups: [] }),
  'Backup data must contain a "studyModes" array.',
);
assertThrows(
  () => validateBackupData({ groups: [], studyModes: [] }),
  'Backup data must contain an "activityHeatmap" object.',
);
assertThrows(
  () => validateBackupData({ groups: [null], studyModes: [], activityHeatmap: {} }),
  'Each group must be a valid object.',
);
assertThrows(
  () => validateBackupData({ groups: [{ id: 123 }], studyModes: [], activityHeatmap: {} }),
  'Each group must have a string id.',
);
console.log('✓ Backup validation tests passed');

// ==========================================
// 7. Atomic Deck Import Validation Tests
// ==========================================
console.log('\n--- Running Import Deck Validation Tests ---');

const normalizedImport = validateImportDeckPayload({
  name: '  Travel Deck  ',
  languages: ['en-US', 'pl-PL'],
  pageNames: ['Front', 'Back'],
  cards: [{ pages: ['hello', 'czesc'] }],
});

assertEqual(normalizedImport.name, 'Travel Deck');
assertEqual(normalizedImport.cards.length, 1);
assertDeepEqual(normalizedImport.cards[0].pages, ['hello', 'czesc']);
assertThrows(
  () =>
    validateImportDeckPayload({
      name: '',
      languages: ['en-US', 'pl-PL'],
      pageNames: ['Front', 'Back'],
      cards: [{ pages: ['hello', 'czesc'] }],
    }),
  'Deck name is required.',
);
assertThrows(
  () =>
    validateImportDeckPayload({
      name: 'Broken',
      languages: ['en-US'],
      pageNames: ['Front', 'Back'],
      cards: [{ pages: ['hello', 'czesc'] }],
    }),
  'Every page must have a language entry.',
);
assertThrows(
  () =>
    validateImportDeckPayload({
      name: 'Broken',
      languages: ['not a lang', 'pl-PL'],
      pageNames: ['Front', 'Back'],
      cards: [{ pages: ['hello', 'czesc'] }],
    }),
  'Invalid language code',
);
assertThrows(
  () =>
    validateImportDeckPayload({
      name: 'Broken',
      languages: ['en-US', 'pl-PL'],
      pageNames: ['Front', 'Back'],
      cards: [{ pages: ['hello'] }],
    }),
  'missing one or more pages',
);
console.log('Import deck validation tests passed');

console.log('\n==========================================');
console.log('All tests completed successfully! 🎉');
console.log('==========================================');
export {};
