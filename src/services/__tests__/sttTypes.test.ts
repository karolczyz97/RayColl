import { describe, it, expect } from '@jest/globals';

import { combineRecognizedText, normalizeSttResult } from '../sttTypes';

describe('normalizeSttResult', () => {
  it('collapses internal whitespace and trims', () => {
    expect(normalizeSttResult('  hello   world  ')).toBe('hello world');
  });
});

describe('combineRecognizedText', () => {
  it('returns the interim when the final part is empty', () => {
    expect(combineRecognizedText('', 'hello')).toBe('hello');
  });

  it('returns the final when the interim part is empty', () => {
    expect(combineRecognizedText('hello', '')).toBe('hello');
  });

  it('dedupes when the interim extends the final', () => {
    expect(combineRecognizedText('hello', 'hello world')).toBe('hello world');
  });

  it('keeps the final when the interim is a suffix already covered', () => {
    expect(combineRecognizedText('hello world', 'world')).toBe('hello world');
  });

  it('concatenates disjoint final and interim parts', () => {
    expect(combineRecognizedText('hello', 'goodbye')).toBe('hello goodbye');
  });

  it('dedupes case-insensitively and locale-independently', () => {
    // Uses toLowerCase (not toLocaleLowerCase): under a Turkish locale the old code
    // mapped "I" -> "ı" on only one side, so "LIMIT" (final) and "limit" (interim)
    // failed to match and were wrongly concatenated as "LIMIT limit value".
    expect(combineRecognizedText('LIMIT', 'limit value')).toBe('limit value');
  });
});
