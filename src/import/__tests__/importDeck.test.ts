import { validateImportDeckPayload } from '../importDeck';

function assertThrows(fn: () => void, message: string) {
  let threw = false;
  try {
    fn();
  } catch {
    threw = true;
  }
  if (!threw) {
    throw new Error(`${message}: expected error but none thrown`);
  }
}

function assertNoThrow(fn: () => void, message: string) {
  try {
    fn();
  } catch (e: unknown) {
    throw new Error(`${message}: unexpected error: ${e instanceof Error ? e.message : String(e)}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Import Deck Tests ---');

  assertThrows(
    () =>
      validateImportDeckPayload({
        name: 'Test',
        languages: ['en-US', ''],
        pageNames: ['A', 'B'],
        cards: [{ pages: ['a', 'b'] }],
      }),
    'Empty language should throw',
  );

  assertNoThrow(
    () =>
      validateImportDeckPayload({
        name: 'Test',
        languages: ['en-US', 'pl-PL'],
        pageNames: ['A', 'B'],
        cards: [{ pages: ['a', 'b'] }],
      }),
    'Valid languages should not throw',
  );

  console.log('Import deck tests passed');
}
