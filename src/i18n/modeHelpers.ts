import type { TranslationFn } from './index';

/**
 * Resolves a study mode's display name: the localized `mode.<id>.name` when a
 * translation exists, otherwise the stored mode name (for user-created modes).
 */
export function getModeName(t: TranslationFn, modeId: string, fallbackName: string): string {
  const key = `mode.${modeId}.name`;
  const translated = t(key);
  return translated === key ? fallbackName : translated;
}
