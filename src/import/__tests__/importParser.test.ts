import { detectSeparator, parseCSV } from '../importParser';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

function assertDeepEqual(actual: unknown, expected: unknown, message: string) {
  const actualJson = JSON.stringify(actual);
  const expectedJson = JSON.stringify(expected);

  if (actualJson !== expectedJson) {
    throw new Error(`${message}: expected ${expectedJson}, got ${actualJson}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Import Parser Tests ---');

  assertEqual(detectSeparator('front\tback\nhello\tczesc'), 'tab', 'Separator detection should find tabs');
  assertDeepEqual(
    parseCSV('front,back\nhello,czesc', 'comma', 2),
    [
      ['front', 'back'],
      ['hello', 'czesc'],
    ],
    'Parser should split CSV rows by detected separator',
  );

  console.log('Import parser tests passed');
}
