import { describe, it, expect } from '@jest/globals';

import {
  detectFirstRowHeader,
  detectLangFromHeader,
  detectPageCount,
  detectSeparator,
  looksLikeHeaderLabel,
  normalizeImportCell,
  parseCSV,
  serializeCSV,
} from '../importParser';

describe('importParser', () => {
  describe('detectSeparator', () => {
    it('finds tabs', () => {
      expect(detectSeparator('front\tback\nhello\tczesc')).toBe('tab');
    });

    it('handles BOM with comma', () => {
      expect(detectSeparator('\uFEFFfront,back\r\nhello,czesc')).toBe('comma');
    });

    it('prefers majority separator in mixed data', () => {
      expect(detectSeparator('a;b;c\nd,e,f\ng;h;i')).toBe('semicolon');
    });

    it('falls back to semicolon when no separator found', () => {
      expect(detectSeparator('hello\nworld')).toBe('semicolon');
    });

    it('detects semicolon in 3-line data', () => {
      expect(detectSeparator('a;b;c\n1;2;3\nx;y;z')).toBe('semicolon');
    });

    it('detects tab in 2-line data', () => {
      expect(detectSeparator('a\tb\tc\n1\t2\t3')).toBe('tab');
    });

    it('ignores newlines inside quoted fields when detecting', () => {
      expect(detectSeparator('"a;b"\n1;2\n3;4')).toBe('semicolon');
    });
  });

  describe('parseCSV', () => {
    it('splits rows by detected separator', () => {
      expect(parseCSV('front,back\nhello,czesc', 'comma', 2)).toEqual([
        ['front', 'back'],
        ['hello', 'czesc'],
      ]);
    });

    it('strips BOM, normalizes CRLF, and unescapes quotes', () => {
      expect(parseCSV('\uFEFFfront;back\r\n"he said ""hi""";czesc', 'semicolon', 2)).toEqual([
        ['front', 'back'],
        ['he said "hi"', 'czesc'],
      ]);
    });

    it('handles multiline quoted fields', () => {
      expect(parseCSV('"line1\nline2";translation\nnext;row', 'semicolon', 2)).toEqual([
        ['line1\nline2', 'translation'],
        ['next', 'row'],
      ]);
    });

    it('handles separators inside quoted fields', () => {
      expect(parseCSV('"has;semicolon";"also;here"\nfoo;bar', 'semicolon', 2)).toEqual([
        ['has;semicolon', 'also;here'],
        ['foo', 'bar'],
      ]);
    });

    it('keeps extra columns beyond pageCount', () => {
      expect(parseCSV('a;b;c;d', 'semicolon', 2)).toEqual([['a', 'b', 'c', 'd']]);
    });

    it('pads to pageCount', () => {
      expect(parseCSV('a;b', 'semicolon', 4)).toEqual([['a', 'b', '', '']]);
    });

    it('keeps extra columns on individual rows', () => {
      expect(parseCSV('a;b\nc;d;e', 'semicolon', 2)).toEqual([['a', 'b'], ['c', 'd', 'e']]);
    });

    it('supports a multi-character custom separator', () => {
      expect(parseCSV('front::back\nhello::czesc', '::', 2)).toEqual([
        ['front', 'back'],
        ['hello', 'czesc'],
      ]);
    });

    it('does not split on a partial multi-character separator', () => {
      // A lone ":" must not break a field when the separator is "::".
      expect(parseCSV('a:b::c:d', '::', 2)).toEqual([['a:b', 'c:d']]);
    });
  });

  describe('detectPageCount', () => {
    it('supports CR-only line endings', () => {
      expect(detectPageCount('front;back\rexample;translation', ';')).toBe(2);
    });

    it('skips first row when header detected', () => {
      expect(detectPageCount('h1;h2\n1;2;3;4;5', ';', true)).toBe(5);
    });

    it('returns count without skipFirstRow', () => {
      expect(detectPageCount('1;2;3;4;5', ';')).toBe(5);
    });

    it('does not clamp 10 columns', () => {
      expect(detectPageCount('h1;h2\n1;2;3;4;5;6;7;8;9;0', ';', true)).toBe(10);
    });

    it('does not clamp over 20 columns', () => {
      expect(detectPageCount('1;2;3;4;5;6;7;8;9;0;11;12;13;14;15;16;17;18;19;20;21', ';')).toBe(21);
    });

    it('handles newlines inside quoted fields', () => {
      expect(detectPageCount('a;"line\nbreak";c\nd;e;f', ';')).toBe(3);
    });
  });

  describe('detectFirstRowHeader', () => {
    it('recognizes common header labels', () => {
      expect(detectFirstRowHeader('Phrase;Translation;Example\nhello;czesc;example', 'semicolon', 3).isLikelyHeader).toBe(true);
    });

    it('treats normal data rows as flashcards', () => {
      expect(detectFirstRowHeader('hello;czesc\nbye;pa', 'semicolon', 2).isLikelyHeader).toBe(false);
    });
  });

  describe('normalizeImportCell', () => {
    it('trims whitespace', () => {
      expect(normalizeImportCell(' hello ')).toBe('hello');
    });

    it('handles empty string', () => {
      expect(normalizeImportCell('')).toBe('');
    });

    it('preserves inner spaces', () => {
      expect(normalizeImportCell('  a  b  ')).toBe('a  b');
    });

    it('handles already-trimmed', () => {
      expect(normalizeImportCell('no_space')).toBe('no_space');
    });
  });

  describe('looksLikeHeaderLabel', () => {
    it('rejects substring matches', () => {
      expect(looksLikeHeaderLabel('swordfish')).toBe(false);
    });

    it('accepts exact word match', () => {
      expect(looksLikeHeaderLabel('Word')).toBe(true);
    });
  });

  describe('detectLangFromHeader', () => {
    it('rejects substring of "word"', () => {
      expect(detectLangFromHeader('swordfish')).toBe('en-US');
    });

    it('matches exact "word"', () => {
      expect(detectLangFromHeader('word')).toBe('en-US');
    });

    it('detects Polish from diacritic-stripped word', () => {
      expect(detectLangFromHeader('Tłumaczenie')).toBe('pl-PL');
    });

    it('detects German from diacritic-stripped word', () => {
      expect(detectLangFromHeader('Übersetzung')).toBe('de-DE');
    });

    it('rejects plural "palabras" as not exact match', () => {
      expect(detectLangFromHeader('palabras')).toBe('en-US');
    });

    it('detects English from prefix "angiel"', () => {
      expect(detectLangFromHeader('Angielski')).toBe('en-US');
    });
  });

  describe('serializeCSV', () => {
    it('serializes simple rows', () => {
      expect(serializeCSV([['hello', 'world'], ['foo', 'bar']], 'semicolon')).toBe('hello;world\nfoo;bar');
    });

    it('wraps separator in quotes', () => {
      expect(serializeCSV([['hello;world', 'foo']], 'semicolon')).toBe('"hello;world";foo');
    });

    it('escapes double quotes', () => {
      expect(serializeCSV([['hello "quote" world', 'bar']], 'semicolon')).toBe('"hello ""quote"" world";bar');
    });

    it('roundtrip serialization and parsing returns identical rows', () => {
      const rows = [['a', 'b'], ['c', 'd;e'], ['f', 'g "h" i']];
      const serialized = serializeCSV(rows, 'semicolon');
      const parsed = parseCSV(serialized, 'semicolon', 2);
      expect(parsed).toEqual(rows);
    });
  });
});
