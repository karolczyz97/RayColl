import { describe, expect, it } from '@jest/globals';

import { en } from '../locales/en';
import { pl } from '../locales/pl';
import { de } from '../locales/de';
import { es } from '../locales/es';
import { it as itLocale } from '../locales/it';

const LOCALES = { pl, de, es, it: itLocale } as const;

describe('i18n locale consistency', () => {
  const enKeys = Object.keys(en).sort();

  it('en locale is not empty', () => {
    expect(enKeys.length).toBeGreaterThan(0);
  });

  for (const [name, locale] of Object.entries(LOCALES)) {
    it(`${name} has the same keys as en`, () => {
      const localeKeys = Object.keys(locale).sort();
      const missing = enKeys.filter((k) => !(k in locale));
      const extra = localeKeys.filter((k) => !enKeys.includes(k));

      expect(missing).toEqual([]);
      expect(extra).toEqual([]);
    });

    it(`${name} has no empty values`, () => {
      const empty = Object.entries(locale as Record<string, string>).filter(
        ([, v]) => v.trim() === '',
      );
      expect(empty).toEqual([]);
    });
  }
});
