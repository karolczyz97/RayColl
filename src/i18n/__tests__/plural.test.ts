import { describe, expect, it } from '@jest/globals';

import { formatCardCount, formatSrsCountLabel, polishPlural } from '../plural';

const forms: [string, string, string] = ['Nowa', 'Nowe', 'Nowych'];

describe('polishPlural', () => {
  it('uses the singular form only for exactly 1', () => {
    expect(polishPlural(1, forms)).toBe('Nowa');
  });

  it('uses the few form for 2-4 outside the teens', () => {
    expect(polishPlural(2, forms)).toBe('Nowe');
    expect(polishPlural(3, forms)).toBe('Nowe');
    expect(polishPlural(4, forms)).toBe('Nowe');
    expect(polishPlural(22, forms)).toBe('Nowe');
    expect(polishPlural(34, forms)).toBe('Nowe');
  });

  it('uses the many form for 0, 5+, and the teens', () => {
    expect(polishPlural(0, forms)).toBe('Nowych');
    expect(polishPlural(5, forms)).toBe('Nowych');
    expect(polishPlural(12, forms)).toBe('Nowych');
    expect(polishPlural(13, forms)).toBe('Nowych');
    expect(polishPlural(14, forms)).toBe('Nowych');
    expect(polishPlural(25, forms)).toBe('Nowych');
  });
});

describe('formatSrsCountLabel', () => {
  const fakeT = (key: string) => ({ 'srs.badge.new': 'New' })[key] ?? key;

  it('declines and puts the count first in Polish', () => {
    expect(formatSrsCountLabel('new', 1, fakeT, 'pl', 'srs.badge.new')).toBe('1 Nowa');
    expect(formatSrsCountLabel('new', 3, fakeT, 'pl', 'srs.badge.new')).toBe('3 Nowe');
    expect(formatSrsCountLabel('new', 20, fakeT, 'pl', 'srs.badge.new')).toBe('20 Nowych');
  });

  it('keeps the "Label (count)" format for other languages', () => {
    expect(formatSrsCountLabel('new', 3, fakeT, 'en', 'srs.badge.new')).toBe('New (3)');
  });
});

describe('formatCardCount', () => {
  // Mimics the real translator: pl declines, others use one/other.
  const dict: Record<string, Record<string, string>> = {
    pl: {
      'cards_count.one': '{count} fiszka',
      'cards_count.few': '{count} fiszki',
      'cards_count.many': '{count} fiszek',
      'cards_count.other': '{count} fiszek',
    },
    en: {
      'cards_count.one': '{count} card',
      'cards_count.few': '{count} cards',
      'cards_count.many': '{count} cards',
      'cards_count.other': '{count} cards',
    },
  };
  const makeT =
    (lang: string) => (key: string, r?: Record<string, string | number>) =>
      (dict[lang][key] ?? key).replace('{count}', String(r?.count ?? ''));

  it('declines "fiszka" correctly in Polish', () => {
    const t = makeT('pl');
    expect(formatCardCount(1, 'pl', t)).toBe('1 fiszka');
    expect(formatCardCount(2, 'pl', t)).toBe('2 fiszki');
    expect(formatCardCount(54, 'pl', t)).toBe('54 fiszki');
    expect(formatCardCount(5, 'pl', t)).toBe('5 fiszek');
    expect(formatCardCount(12, 'pl', t)).toBe('12 fiszek');
  });

  it('uses singular/plural for other languages', () => {
    const t = makeT('en');
    expect(formatCardCount(1, 'en', t)).toBe('1 card');
    expect(formatCardCount(54, 'en', t)).toBe('54 cards');
  });
});
