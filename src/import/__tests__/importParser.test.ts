import {
  detectFirstRowHeader,
  detectLangFromHeader,
  detectPageCount,
  detectSeparator,
  looksLikeHeaderLabel,
  normalizeImportCell,
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

  // --- normalizeImportCell ---
  assertEqual(normalizeImportCell(' hello '), 'hello', 'normalizeImportCell trims whitespace');
  assertEqual(normalizeImportCell(''), '', 'normalizeImportCell handles empty string');
  assertEqual(normalizeImportCell('  a  b  '), 'a  b', 'normalizeImportCell preserves inner spaces');
  assertEqual(normalizeImportCell('no_space'), 'no_space', 'normalizeImportCell handles already-trimmed');

  // --- looksLikeHeaderLabel ---
  assertEqual(looksLikeHeaderLabel('swordfish'), false, 'looksLikeHeaderLabel: substring match should not count');
  assertEqual(looksLikeHeaderLabel('Word'), true, 'looksLikeHeaderLabel: exact word match');

  // --- detectLangFromHeader ---
  assertEqual(detectLangFromHeader('swordfish'), 'en-US', 'detectLangFromHeader: substring of "word" should not match');
  assertEqual(detectLangFromHeader('word'), 'en-US', 'detectLangFromHeader: exact "word"');
  assertEqual(detectLangFromHeader('Tłumaczenie'), 'pl-PL', 'detectLangFromHeader: diacritic stripped Polish');
  assertEqual(detectLangFromHeader('Übersetzung'), 'de-DE', 'detectLangFromHeader: diacritic stripped German');
  assertEqual(detectLangFromHeader('palabras'), 'en-US', 'detectLangFromHeader: plural "palabras" not exact match');
  assertEqual(detectLangFromHeader('Angielski'), 'en-US', 'detectLangFromHeader: prefix "angiel"');

  // --- detectSeparator ---
  assertEqual(detectSeparator('a;b;c\n1;2;3\nx;y;z'), 'semicolon', 'detectSeparator: 3-line semicolon');
  assertEqual(detectSeparator('a\tb\tc\n1\t2\t3'), 'tab', 'detectSeparator: 2-line tab');
  assertEqual(detectSeparator('a;b;c\nd,e,f\ng;h;i'), 'semicolon', 'detectSeparator: mixed with semicolon majority');
  assertEqual(detectSeparator('hello\nworld'), 'semicolon', 'detectSeparator: fallback for no separator');

  // --- detectPageCount ---
  assertEqual(detectPageCount('h1;h2\n1;2;3;4;5', ';', true), 5, 'detectPageCount: skipFirstRow skips 2-col header');
  assertEqual(detectPageCount('1;2;3;4;5', ';'), 5, 'detectPageCount: no skipFirstRow');
  assertEqual(detectPageCount('h1;h2\n1;2;3;4;5;6;7;8;9;0', ';', true), 10, 'detectPageCount: no clamp to MAX_STORED (10 cols)');
  assertEqual(detectPageCount('1;2;3;4;5;6;7;8;9;0;11;12;13;14;15;16;17;18;19;20;21', ';'), 21, 'detectPageCount: no clamp for >20 cols');

  // --- parseCSV (no slice) ---
  assertDeepEqual(
    parseCSV('a;b;c;d', 'semicolon', 2),
    [['a', 'b', 'c', 'd']],
    'parseCSV: keeps extra columns beyond pageCount',
  );
  assertDeepEqual(
    parseCSV('a;b', 'semicolon', 4),
    [['a', 'b', '', '']],
    'parseCSV: pads to pageCount',
  );
  assertDeepEqual(
    parseCSV('a;b\nc;d;e', 'semicolon', 2),
    [['a', 'b'], ['c', 'd', 'e']],
    'parseCSV: row with more columns than pageCount keeps them',
  );

  console.log('Import parser tests passed');
}
