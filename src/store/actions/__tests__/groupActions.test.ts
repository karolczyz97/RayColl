import { setVisiblePageCountAction } from '../groupActions';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Group Actions Tests ---');

  const group = {
    id: 'g1',
    name: 'Deck',
    cards: [],
    activeModeId: 'classic',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
  };

  const expanded = setVisiblePageCountAction([group], 'g1', 4)[0];
  assertEqual(expanded.pageNames[0], 'Front', 'Existing page names should stay in place');
  assertEqual(expanded.pageNames[1], 'Back', 'Existing page names should preserve order');
  assertEqual(expanded.pageLanguages[0], 'en-US', 'Existing page languages should stay in place');
  assertEqual(expanded.pageLanguages[1], 'pl-PL', 'Existing page languages should preserve order');
  assertEqual(expanded.pageNames[2], 'Page 3', 'New page names should be generated for added pages');
  assertEqual(expanded.pageLanguages[2], 'en-US', 'New page languages should default to en-US');

  console.log('Group actions tests passed');
}
