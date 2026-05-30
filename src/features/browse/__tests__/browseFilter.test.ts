import { toggleCategoryReducer, shouldShowCard } from '../browseFilter';
import type { SrsCardCategory } from '../../../srs/srsEngine';

const ALL: SrsCardCategory[] = ['new', 'learning', 'review', 'mastered'];

function assertDeepEqual<T>(actual: T, expected: T, msg?: string) {
  const aStr = JSON.stringify(actual);
  const eStr = JSON.stringify(expected);
  if (aStr !== eStr) {
    throw new Error(`Assertion failed: expected ${eStr}, got ${aStr}${msg ? ` - ${msg}` : ''}`);
  }
}

function assertEqual(actual: boolean | number, expected: boolean | number, msg?: string) {
  if (actual !== expected) {
    throw new Error(
      `Assertion failed: expected ${expected}, got ${actual}${msg ? ` - ${msg}` : ''}`,
    );
  }
}

export async function runTests() {
  let passed = 0;
  let failed = 0;

  const run = (name: string, fn: () => void) => {
    try {
      fn();
      passed++;
    } catch (err) {
      failed++;
      console.error(`  FAIL: ${name}`);
      console.error(`    ${(err as Error).message}`);
    }
  };

  console.log('browseFilter tests:');

  // toggleCategoryReducer — all 4 categories non-empty
  const nonEmpty4: SrsCardCategory[] = ['new', 'learning', 'review', 'mastered'];

  run('adds category when none selected', () => {
    assertDeepEqual(toggleCategoryReducer([], 'new', nonEmpty4), ['new']);
  });

  run('adds second category', () => {
    assertDeepEqual(toggleCategoryReducer(['new'], 'review', nonEmpty4), ['new', 'review']);
  });

  run('removes category when already selected', () => {
    assertDeepEqual(toggleCategoryReducer(['new', 'review'], 'new', nonEmpty4), ['review']);
  });

  run('resets to [] when all 4 would be selected', () => {
    assertDeepEqual(toggleCategoryReducer(['new', 'review', 'mastered'], 'learning', nonEmpty4), []);
  });

  run('does NOT reset when only 3 selected', () => {
    assertDeepEqual(
      toggleCategoryReducer(['new', 'review'], 'mastered', nonEmpty4),
      ['new', 'review', 'mastered'],
    );
  });

  run('toggling same category twice returns to original', () => {
    const step1 = toggleCategoryReducer([], 'new', nonEmpty4);
    const step2 = toggleCategoryReducer(step1, 'new', nonEmpty4);
    assertDeepEqual(step2, []);
  });

  run('full cycle: select all one by one → reset', () => {
    let state: SrsCardCategory[] = [];
    for (const cat of ALL) {
      state = toggleCategoryReducer(state, cat, nonEmpty4);
    }
    assertDeepEqual(state, []);
  });

  run('select 3 then deselect one → stays filtered', () => {
    let state: SrsCardCategory[] = [];
    for (const cat of ALL.slice(0, 3)) {
      state = toggleCategoryReducer(state, cat, nonEmpty4);
    }
    assertDeepEqual(state, ['new', 'learning', 'review']);
    state = toggleCategoryReducer(state, 'new', nonEmpty4);
    assertDeepEqual(state, ['learning', 'review']);
  });

  run('toggling last remaining → resets', () => {
    assertDeepEqual(toggleCategoryReducer(['new'], 'new', nonEmpty4), []);
  });

  // Non-empty aware reset: only 3 categories have cards
  const nonEmpty3: SrsCardCategory[] = ['new', 'learning', 'review'];

  run('resets when all 3 non-empty selected (mastered is empty)', () => {
    assertDeepEqual(
      toggleCategoryReducer(['new', 'learning'], 'review', nonEmpty3),
      [],
    );
  });

  run('does NOT reset when selecting non-empty + empty category', () => {
    const nonEmpty2: SrsCardCategory[] = ['new', 'review'];
    assertDeepEqual(
      toggleCategoryReducer(['new'], 'mastered', nonEmpty2),
      ['new', 'mastered'],
    );
  });

  run('resets when last non-empty is selected (ignoring empty)', () => {
    const nonEmpty1: SrsCardCategory[] = ['review'];
    assertDeepEqual(
      toggleCategoryReducer(['review'], 'review', nonEmpty1),
      [],
    );
  });

  // shouldShowCard
  run('shows all cards when no categories selected', () => {
    for (const cat of ALL) {
      assertEqual(shouldShowCard([], cat), true, cat);
    }
  });

  run('shows only matching category', () => {
    assertEqual(shouldShowCard(['new'], 'new'), true);
    assertEqual(shouldShowCard(['new'], 'learning'), false);
    assertEqual(shouldShowCard(['new'], 'review'), false);
    assertEqual(shouldShowCard(['new'], 'mastered'), false);
  });

  run('shows multiple matching categories', () => {
    const selected: SrsCardCategory[] = ['new', 'review'];
    assertEqual(shouldShowCard(selected, 'new'), true);
    assertEqual(shouldShowCard(selected, 'learning'), false);
    assertEqual(shouldShowCard(selected, 'review'), true);
    assertEqual(shouldShowCard(selected, 'mastered'), false);
  });

  console.log(`  ${passed} passed, ${failed} failed`);
  if (failed > 0) throw new Error(`${failed} test(s) failed`);
}