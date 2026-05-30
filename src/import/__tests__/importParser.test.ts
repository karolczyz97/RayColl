import {
  detectFirstRowHeader,
  detectPageCount,
  detectSeparator,
  parseCSV,
  parseCSVLine,
} from '../importParser';

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
  assertEqual(detectSeparator('\uFEFFfront,back\r\nhello,czesc'), 'comma', 'BOM should not affect separator detection');
  assertEqual(detectPageCount('front;back\rexample;translation', ';'), 2, 'Parser should support CR-only line endings');
  assertDeepEqual(
    parseCSV('\uFEFFfront;back\r\n"he said ""hi""";czesc', 'semicolon', 2),
    [
      ['front', 'back'],
      ['he said "hi"', 'czesc'],
    ],
    'Parser should strip BOM, normalize CRLF, and unescape quotes',
  );
  assertDeepEqual(
    parseCSVLine(`${'a'.repeat(10000)};tail`, ';'),
    ['a'.repeat(10000), 'tail'],
    'Parser should handle long fields without truncating them',
  );

  assertDeepEqual(
    parseCSV('"line1\nline2";translation\nnext;row', 'semicolon', 2),
    [
      ['line1\nline2', 'translation'],
      ['next', 'row'],
    ],
    'Parser should handle multiline quoted fields',
  );

  assertDeepEqual(
    parseCSV('"has;semicolon";"also;here"\nfoo;bar', 'semicolon', 2),
    [
      ['has;semicolon', 'also;here'],
      ['foo', 'bar'],
    ],
    'Parser should handle separators inside quoted fields',
  );
  assertEqual(
    detectFirstRowHeader('Phrase;Translation;Example\nhello;czesc;example', 'semicolon', 3)
      .isLikelyHeader,
    true,
    'Header detection should recognize common header labels',
  );
  assertEqual(
    detectFirstRowHeader('hello;czesc\nbye;pa', 'semicolon', 2).isLikelyHeader,
    false,
    'Header detection should keep normal data rows as flashcards',
  );

  console.log('Import parser tests passed');
}
