import { MIN_PAGE_COUNT } from '@/constants/pages';
import { parseCSVRaw } from './csvParser';

export const HEADER_TOKENS = [
  'word',
  'phrase',
  'translation',
  'example',
  'front',
  'back',
  'meaning',
  'definition',
  'english',
  'polish',
  'german',
  'deutsch',
  'french',
  'francais',
  'italian',
  'italiano',
  'spanish',
  'espanol',
  'palabra',
  'traduccion',
  'ejemplo',
  'wort',
  'ubersetzung',
  'beispiel',
  'slowo',
  'tlumaczenie',
  'przyklad',
  'angielski',
  'polski',
  'niemiecki',
  'francuski',
  'wloski',
  'hiszpanski',
];

function normalizeHeaderToken(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim();
}

export function looksLikeHeaderLabel(value: string): boolean {
  const normalized = normalizeHeaderToken(value);
  if (!normalized) {
    return false;
  }

  const words = normalized.split(/\s+/);
  return words.some((word) => HEADER_TOKENS.includes(word));
}

function looksLikeShortLabel(value: string): boolean {
  const normalized = value.trim();
  if (!normalized) {
    return true;
  }

  const wordCount = normalized.split(/\s+/).length;
  return normalized.length <= 24 && wordCount <= 3;
}

function looksLikeDataValue(value: string): boolean {
  const normalized = value.trim();
  if (!normalized) {
    return false;
  }

  const wordCount = normalized.split(/\s+/).length;
  const hasSentenceLikeLength = normalized.length > 24 || wordCount >= 5;
  const hasDigits = /\d/.test(normalized);
  return hasSentenceLikeLength || hasDigits;
}

export function detectPageCount(text: string, sepKey: string, skipFirstRow?: boolean): number {
  const rows = parseCSVRaw(text, sepKey).filter((r) => r.some((f) => f.trim()));
  const dataRows = skipFirstRow && rows.length > 1 ? rows.slice(1) : rows;
  if (dataRows.length === 0) return MIN_PAGE_COUNT;
  const counts = dataRows.map((r) => r.length);
  const maxCols = Math.max(...counts);
  return Math.max(MIN_PAGE_COUNT, maxCols);
}

export function detectFirstRowHeader(
  text: string,
  sepKey: string,
  expectedPageCount?: number,
): { isLikelyHeader: boolean; confidence: number } {
  const rows = parseCSVRaw(text, sepKey);
  if (rows.length === 0) {
    return { isLikelyHeader: false, confidence: 0 };
  }

  const firstRow = rows[0];
  const firstFields = firstRow
    .slice(0, expectedPageCount ?? firstRow.length)
    .map((field) => field.trim())
    .filter((field) => field.length > 0);
  if (firstFields.length === 0) {
    return { isLikelyHeader: false, confidence: 0 };
  }

  const secondRow = rows[1] ?? [];
  let score = 0;
  const keywordMatches = firstFields.filter(looksLikeHeaderLabel).length;
  const shortLabelMatches = firstFields.filter(looksLikeShortLabel).length;
  const dataLikeMatches = firstFields.filter(looksLikeDataValue).length;

  if (keywordMatches > 0) {
    score += keywordMatches >= 2 ? 0.65 : 0.45;
  }
  if (shortLabelMatches === firstFields.length) {
    score += 0.15;
  }
  if (secondRow.length > 0 && Math.abs(secondRow.length - firstRow.length) <= 1) {
    score += 0.1;
  }
  if (dataLikeMatches > 0) {
    score -= Math.min(0.35, dataLikeMatches * 0.15);
  }

  const confidence = Math.max(0, Math.min(1, Number(score.toFixed(2))));
  return {
    isLikelyHeader: confidence >= 0.5,
    confidence,
  };
}
