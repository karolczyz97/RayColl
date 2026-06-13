import type { SrsCardCategory } from '@/srs/srsEngine';

export type PluralCategory = 'zero' | 'one' | 'two' | 'few' | 'many' | 'other';
/**
 * CLDR plural category for `count` in `language`. Uses the built-in
 * `Intl.PluralRules` when the runtime provides it (web + modern Hermes), and
 * falls back to hand-written rules for the app's locales so it never throws on
 * an engine without Intl data.
 */
export function selectPluralCategory(count: number, language: string): PluralCategory {
  try {
    if (typeof Intl !== 'undefined' && typeof Intl.PluralRules === 'function') {
      return new Intl.PluralRules(language).select(count) as PluralCategory;
    }
  } catch {
    // Older engine / missing locale data — fall through to the manual rules.
  }
  return fallbackPluralCategory(count, language);
}

function fallbackPluralCategory(count: number, language: string): PluralCategory {
  const n = Math.abs(count);
  if (language === 'pl') {
    if (n === 1) return 'one';
    const mod10 = n % 10;
    const mod100 = n % 100;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return 'few';
    return 'many';
  }
  // en, de, es, it (and any other supported locale): singular only for one.
  return n === 1 ? 'one' : 'other';
}

/**
 * Picks one of three Polish forms [one, few, many] for `count`.
 */
export function polishPlural(count: number, [one, few, many]: [string, string, string]): string {
  const category = selectPluralCategory(count, 'pl');
  if (category === 'one') return one;
  if (category === 'few') return few;
  return many;
}

/**
 * "{count} <noun>" with the noun declined for the active language. Locales only
 * define the categories they actually use (pl → one/few/many, others →
 * one/other), so any other CLDR category is collapsed into the right bucket.
 */
export function formatCardCount(
  count: number,
  language: string,
  t: (key: string, replacements?: Record<string, string | number>) => string,
): string {
  const category = selectPluralCategory(count, language);
  let bucket = category;
  if (language === 'pl' && category !== 'one' && category !== 'few') {
    bucket = 'many';
  } else if (language !== 'pl' && category !== 'one') {
    bucket = 'other';
  }
  return t(`cards_count.${bucket}`, { count });
}

/**
 * Polish declensions for each category, agreeing with "fiszka" (feminine):
 * [one, few, many]. Used to build "1 Nowa", "2 Nowe", "5 Nowych" style labels.
 * "W toku" is an invariant phrase, so all three forms are identical.
 */
export const PL_SRS_PLURALS: Record<SrsCardCategory, [string, string, string]> = {
  new: ['Nowa', 'Nowe', 'Nowych'],
  learning: ['W toku', 'W toku', 'W toku'],
  review: ['Powtórka', 'Powtórki', 'Powtórek'],
  mastered: ['Opanowana', 'Opanowane', 'Opanowanych'],
};

/**
 * Count-prefixed category label. In Polish it declines the noun and puts the
 * count first ("5 Nowych"); other languages keep "Label (count)".
 */
export function formatSrsCountLabel(
  category: SrsCardCategory,
  count: number,
  t: (key: string) => string,
  language: string,
  fallbackTokenLabelKey: string,
): string {
  if (language === 'pl') {
    return `${count} ${polishPlural(count, PL_SRS_PLURALS[category])}`;
  }
  return `${t(fallbackTokenLabelKey)} (${count})`;
}
