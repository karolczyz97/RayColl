import { MIN_PAGE_COUNT } from '../constants/pages';
import { padArray } from '../utils/array';

export const SEPARATORS: Record<string, string> = {
  tab: '\t',
  semicolon: ';',
  comma: ',',
  pipe: '|',
};

const HEADER_TOKENS = [
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

const LANG_TOKENS_EN_EXACT = ['word', 'phrase', 'english', 'translation', 'example', 'front', 'back', 'meaning', 'definition', 'en'];
const LANG_TOKENS_EN_PREFIX = ['angiel'];
const LANG_TOKENS_PL_EXACT = ['słowo', 'polish', 'pl', 'tłumaczenie', 'przykład'];
const LANG_TOKENS_PL_PREFIX = ['polsk', 'fraz'];
const LANG_TOKENS_ES_EXACT = ['palabra', 'es', 'spanish', 'espanol', 'traduccion', 'ejemplo'];
const LANG_TOKENS_ES_PREFIX = ['hiszpan'];
const LANG_TOKENS_DE_EXACT = ['wort', 'deutsch', 'de', 'ubersetzung', 'beispiel'];
const LANG_TOKENS_DE_PREFIX = ['niemiec'];
const LANG_TOKENS_FR_EXACT = ['french', 'francais', 'fr'];
const LANG_TOKENS_FR_PREFIX = ['franc'];
const LANG_TOKENS_IT_EXACT = ['italiano', 'italian', 'it'];
const LANG_TOKENS_IT_PREFIX = ['włos'];

export function normalizeImportCell(value: string): string {
  return value.trim();
}

function normalizeCsvText(text: string): string {
  return text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
}

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

export function parseCSVLine(line: string, sep: string): string[] {
  const result: string[] = [];
  let currentField = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') {
      if (inQuotes && line[i + 1] === '"') {
        currentField += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === sep && !inQuotes) {
      result.push(normalizeImportCell(currentField));
      currentField = '';
    } else {
      currentField += char;
    }
  }
  result.push(normalizeImportCell(currentField));
  return result;
}

export function detectSeparator(text: string): string {
  const lines = normalizeCsvText(text)
    .split('\n')
    .filter((l) => l.trim())
    .slice(0, 5);

  if (lines.length === 0) return 'semicolon';

  let bestKey = 'semicolon';
  let bestScore = 0;

  for (const [key, sep] of Object.entries(SEPARATORS)) {
    let totalCount = 0;
    let consistentLines = 0;

    for (const line of lines) {
      const count = parseCSVLine(line, sep).length - 1;
      if (count > 0) {
        totalCount += count;
        consistentLines++;
      }
    }

    const score = totalCount * (consistentLines / lines.length);
    if (score > bestScore) {
      bestScore = score;
      bestKey = key;
    }
  }

  return bestScore > 0 ? bestKey : 'semicolon';
}

export function detectPageCount(text: string, sepKey: string, skipFirstRow?: boolean): number {
  const sep = resolveSep(sepKey);
  const allLines = normalizeCsvText(text)
    .split('\n')
    .filter((l) => l.trim());
  const dataLines = skipFirstRow && allLines.length > 1 ? allLines.slice(1) : allLines;
  if (dataLines.length === 0) return MIN_PAGE_COUNT;
  const counts = dataLines.map((l) => parseCSVLine(l, sep).length);
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

export function detectLangFromHeader(header: string): string {
  const h = normalizeHeaderToken(header);
  const words = h.split(/\s+/);

  if (words.some((w) => LANG_TOKENS_EN_EXACT.includes(w) || LANG_TOKENS_EN_PREFIX.some((p) => w.startsWith(p)))) return 'en-US';
  if (words.some((w) => LANG_TOKENS_PL_EXACT.includes(w) || LANG_TOKENS_PL_PREFIX.some((p) => w.startsWith(p)))) return 'pl-PL';
  if (words.some((w) => LANG_TOKENS_ES_EXACT.includes(w) || LANG_TOKENS_ES_PREFIX.some((p) => w.startsWith(p)))) return 'es-ES';
  if (words.some((w) => LANG_TOKENS_DE_EXACT.includes(w) || LANG_TOKENS_DE_PREFIX.some((p) => w.startsWith(p)))) return 'de-DE';
  if (words.some((w) => LANG_TOKENS_FR_EXACT.includes(w) || LANG_TOKENS_FR_PREFIX.some((p) => w.startsWith(p)))) return 'fr-FR';
  if (words.some((w) => LANG_TOKENS_IT_EXACT.includes(w) || LANG_TOKENS_IT_PREFIX.some((p) => w.startsWith(p)))) return 'it-IT';

  return 'en-US';
}

function resolveSep(keyOrChar: string): string {
  return SEPARATORS[keyOrChar] ?? keyOrChar;
}

function parseFullText(text: string, sep: string): string[][] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentField = '';
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (char === '"') {
      if (inQuotes && text[i + 1] === '"') {
        currentField += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === sep && !inQuotes) {
      currentRow.push(normalizeImportCell(currentField));
      currentField = '';
    } else if (char === '\n' && !inQuotes) {
      currentRow.push(normalizeImportCell(currentField));
      if (currentRow.some((f) => f.trim())) {
        rows.push(currentRow);
      }
      currentRow = [];
      currentField = '';
    } else {
      currentField += char;
    }
  }

  currentRow.push(normalizeImportCell(currentField));
  if (currentRow.some((f) => f.trim())) {
    rows.push(currentRow);
  }

  return rows;
}

export function parseCSV(text: string, sepKey: string, pageCount: number): string[][] {
  const sep = resolveSep(sepKey);
  return parseFullText(normalizeCsvText(text), sep).map((parts) => padArray(parts, pageCount, ''));
}

export function parseCSVRaw(text: string, sepKey: string): string[][] {
  const sep = resolveSep(sepKey);
  return parseFullText(normalizeCsvText(text), sep);
}

export function serializeCSV(rows: string[][], sepKey: string): string {
  const sep = resolveSep(sepKey);
  return rows
    .map((row) =>
      row
        .map((field) => {
          let escaped = field.replace(/"/g, '""');
          if (escaped.includes(sep) || escaped.includes('"') || escaped.includes('\n')) {
            escaped = `"${escaped}"`;
          }
          return escaped;
        })
        .join(sep)
    )
    .join('\n');
}

