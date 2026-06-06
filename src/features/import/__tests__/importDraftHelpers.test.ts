import { describe, it, expect } from '@jest/globals';

import {
  buildImportSyncKey,
  getHeaderRowFromText,
  getPreviewRows,
  replaceHeaderRowInText,
  serializeImportSourceText,
} from '../importDraftHelpers';

describe('importDraftHelpers', () => {
  describe('getPreviewRows', () => {
    it('skips the first row when header mode is enabled', () => {
      expect(getPreviewRows('Phrase;Translation\nhello;czesc\nbye;pa', 'semicolon', 2, true)).toEqual([
        ['hello', 'czesc'],
        ['bye', 'pa'],
      ]);
    });

    it('keeps the first row when header mode is disabled', () => {
      expect(getPreviewRows('Phrase;Translation\nhello;czesc', 'semicolon', 2, false)).toEqual([
        ['Phrase', 'Translation'],
        ['hello', 'czesc'],
      ]);
    });
  });

  describe('getHeaderRowFromText', () => {
    it('returns the normalized first row', () => {
      expect(getHeaderRowFromText('Phrase;Translation\nhello;czesc', 'semicolon', 2)).toEqual([
        'Phrase',
        'Translation',
      ]);
    });
  });

  describe('replaceHeaderRowInText', () => {
    it('preserves the body rows when replacing the header', () => {
      expect(
        replaceHeaderRowInText('Phrase;Translation\nhello;czesc', 'semicolon', 2, ['Word', 'Example']),
      ).toBe('Word;Example\nhello;czesc');
    });

    it('preserves body fields with semicolon separator', () => {
      expect(
        replaceHeaderRowInText('Phrase;Translation\nhello;"czesc, world"', 'semicolon', 2, ['Word', 'Example']),
      ).toBe('Word;Example\nhello;czesc, world');
    });
  });

  describe('serializeImportSourceText', () => {
    it('prepends the header row in header mode', () => {
      expect(
        serializeImportSourceText([['hello', 'czesc']], 'semicolon', 2, true, ['Phrase', 'Translation']),
      ).toBe('Phrase;Translation\nhello;czesc');
    });
  });

  describe('buildImportSyncKey', () => {
    it('changes when text content changes even at the same length', () => {
      const a = buildImportSyncKey('ab\ncd', 'comma', 2, false);
      const b = buildImportSyncKey('ba\ncd', 'comma', 2, false);
      expect(a).not.toBe(b);
    });

    it('is stable for identical inputs', () => {
      expect(buildImportSyncKey('ab', 'comma', 2, false)).toBe(
        buildImportSyncKey('ab', 'comma', 2, false),
      );
    });

    it('changes when sep, pageCount, or header flag changes', () => {
      const base = buildImportSyncKey('ab', 'comma', 2, false);
      expect(buildImportSyncKey('ab', 'semicolon', 2, false)).not.toBe(base);
      expect(buildImportSyncKey('ab', 'comma', 3, false)).not.toBe(base);
      expect(buildImportSyncKey('ab', 'comma', 2, true)).not.toBe(base);
    });
  });
});
