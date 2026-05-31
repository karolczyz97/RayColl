import {
  getHeaderRowFromText,
  getPreviewRows,
  replaceHeaderRowInText,
  serializeImportSourceText,
} from '../importDraftHelpers';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
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
  console.log('\n--- Running Import Draft Helper Tests ---');

  assertDeepEqual(
    getPreviewRows('Phrase;Translation\nhello;czesc\nbye;pa', 'semicolon', 2, true),
    [
      ['hello', 'czesc'],
      ['bye', 'pa'],
    ],
    'Preview rows should skip the first row when header mode is enabled',
  );

  assertDeepEqual(
    getPreviewRows('Phrase;Translation\nhello;czesc', 'semicolon', 2, false),
    [
      ['Phrase', 'Translation'],
      ['hello', 'czesc'],
    ],
    'Preview rows should keep the first row when header mode is disabled',
  );

  assertDeepEqual(
    getHeaderRowFromText('Phrase;Translation\nhello;czesc', 'semicolon', 2),
    ['Phrase', 'Translation'],
    'Header extraction should return the normalized first row',
  );

  assertEqual(
    replaceHeaderRowInText(
      'Phrase;Translation\nhello;czesc',
      'semicolon',
      2,
      ['Word', 'Example'],
    ),
    'Word;Example\nhello;czesc',
    'Replacing the header row should preserve the body rows',
  );

  assertEqual(
    serializeImportSourceText(
      [['hello', 'czesc']],
      'semicolon',
      2,
      true,
      ['Phrase', 'Translation'],
    ),
    'Phrase;Translation\nhello;czesc',
    'Serializing import text should prepend the header row in header mode',
  );

  assertEqual(
    replaceHeaderRowInText(
      'Phrase;Translation\nhello;"czesc, world"',
      'semicolon',
      2,
      ['Word', 'Example'],
    ),
    'Word;Example\nhello;czesc, world',
    'Replacing the header row should preserve body fields (semicolon separator needs no quotes)',
  );

  console.log('Import draft helper tests passed');
}
