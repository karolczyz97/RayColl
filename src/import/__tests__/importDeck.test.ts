import { describe, it, expect } from '@jest/globals';

import { validateImportDeckPayload } from '@/import/importDeck';
import { MAX_STORED_PAGE_COUNT } from '@/constants/pages';

describe('importDeck', () => {
  it('throws on empty language', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'Test',
        languages: ['en-US', ''],
        pageNames: ['A', 'B'],
        cards: [{ pages: ['a', 'b'] }],
      }),
    ).toThrow(/language/i);
  });

  it('accepts valid languages', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'Test',
        languages: ['en-US', 'pl-PL'],
        pageNames: ['A', 'B'],
        cards: [{ pages: ['a', 'b'] }],
      }),
    ).not.toThrow();
  });

  it('throws when fewer languages than pageNames', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'Test',
        languages: ['en-US'],
        pageNames: ['A', 'B', 'C'],
        cards: [{ pages: ['a', 'b', 'c'] }],
      }),
    ).toThrow(/page.*language/i);
  });

  it('trims deck name and normalizes cards', () => {
    const result = validateImportDeckPayload({
      name: '  Travel Deck  ',
      languages: ['en-US', 'pl-PL'],
      pageNames: ['Front', 'Back'],
      cards: [{ pages: ['hello', 'czesc'] }],
    });
    expect(result.name).toBe('Travel Deck');
    expect(result.cards.length).toBe(1);
    expect(result.cards[0].pages).toEqual(['hello', 'czesc']);
  });

  it('throws on empty name', () => {
    expect(() =>
      validateImportDeckPayload({
        name: '',
        languages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        cards: [{ pages: ['hello', 'czesc'] }],
      }),
    ).toThrow('Deck name is required.');
  });

  it('throws when language count mismatches pageName count', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'Broken',
        languages: ['en-US'],
        pageNames: ['Front', 'Back'],
        cards: [{ pages: ['hello', 'czesc'] }],
      }),
    ).toThrow('Every page must have a language entry.');
  });

  it('throws on invalid language tag', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'Broken',
        languages: ['not a lang', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        cards: [{ pages: ['hello', 'czesc'] }],
      }),
    ).toThrow('Missing language for page 1');
  });

  it('throws on card with fewer pages than expected', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'Broken',
        languages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
        cards: [{ pages: ['hello'] }],
      }),
    ).toThrow('missing one or more pages');
  });

  it('throws on fewer than min pages', () => {
    expect(() =>
      validateImportDeckPayload({
        name: 'TooFew',
        languages: ['en-US'],
        pageNames: ['Only'],
        cards: [{ pages: ['one'] }],
      }),
    ).toThrow('must contain at least');
  });

  it('throws on more than MAX_STORED_PAGE_COUNT pages', () => {
    const manyNames = Array.from({ length: MAX_STORED_PAGE_COUNT + 1 }, (_, i) => `Page ${i + 1}`);
    const manyLangs = Array.from({ length: MAX_STORED_PAGE_COUNT + 1 }, () => 'en-US');
    const manyCardPages = Array.from({ length: MAX_STORED_PAGE_COUNT + 1 }, (_, i) => `data${i + 1}`);
    expect(() =>
      validateImportDeckPayload({
        name: 'TooMany',
        languages: manyLangs,
        pageNames: manyNames,
        cards: [{ pages: manyCardPages }],
      }),
    ).toThrow('can contain at most');
  });

  it('allows 6 pages (>5 visible, <=MAX_STORED)', () => {
    const sixNames = ['A', 'B', 'C', 'D', 'E', 'F'];
    const sixLangs = ['en-US', 'en-US', 'en-US', 'en-US', 'en-US', 'en-US'];
    const sixCardPages = ['1', '2', '3', '4', '5', '6'];
    const result = validateImportDeckPayload({
      name: 'Six Pages',
      languages: sixLangs,
      pageNames: sixNames,
      cards: [{ pages: sixCardPages }],
    });
    expect(result.pageNames.length).toBe(6);
    expect(result.cards[0].pages.length).toBe(6);
  });
});
