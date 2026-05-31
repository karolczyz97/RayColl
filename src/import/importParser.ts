import { MIN_PAGE_COUNT, MAX_STORED_PAGE_COUNT } from '../constants/pages';

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

function looksLikeHeaderLabel(value: string): boolean {
  const normalized = normalizeHeaderToken(value);
  if (!normalized) {
    return false;
  }

  return HEADER_TOKENS.some((token) => normalized.includes(token));
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
      result.push(currentField.trim());
      currentField = '';
    } else {
      currentField += char;
    }
  }
  result.push(currentField.trim());
  return result;
}

export function detectSeparator(text: string): string {
  const firstLine = normalizeCsvText(text)
    .split('\n')
    .find((l) => l.trim()) || '';
  const counts: Record<string, number> = {};
  for (const [key, sep] of Object.entries(SEPARATORS)) {
    counts[key] = parseCSVLine(firstLine, sep).length - 1;
  }
  let best = 'semicolon';
  for (const [key, count] of Object.entries(counts)) {
    if (count > (counts[best] || 0)) best = key;
  }
  return counts[best] > 0 ? best : 'semicolon';
}

export function detectPageCount(text: string, sep: string): number {
  const lines = normalizeCsvText(text)
    .split('\n')
    .filter((l) => l.trim());
  if (lines.length === 0) return MIN_PAGE_COUNT;
  const counts = lines.map((l) => parseCSVLine(l, sep).length);
  const maxCols = Math.max(...counts);
  return Math.max(MIN_PAGE_COUNT, Math.min(MAX_STORED_PAGE_COUNT, maxCols));
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
  const h = header.toLowerCase().trim();
  if (
    h.includes('word') ||
    h.includes('phrase') ||
    h.includes('english') ||
    h.includes('angiel') ||
    h === 'en'
  ) {
    return 'en-US';
  }
  if (h.includes('słowo') || h.includes('fraz') || h.includes('polsk') || h === 'pl') {
    return 'pl-PL';
  }
  if (h.includes('palabra') || h.includes('español') || h.includes('hiszpań') || h === 'es') {
    return 'es-ES';
  }
  if (h.includes('wort') || h.includes('deutsch') || h.includes('niemiec') || h === 'de') {
    return 'de-DE';
  }
  if (h.includes('french') || h.includes('franc') || h === 'fr') {
    return 'fr-FR';
  }
  if (h.includes('ital') || h.includes('włos') || h === 'it') {
    return 'it-IT';
  }
  return 'en-US'; // default fallback
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
      currentRow.push(currentField.trim());
      currentField = '';
    } else if (char === '\n' && !inQuotes) {
      currentRow.push(currentField.trim());
      if (currentRow.some((f) => f.trim())) {
        rows.push(currentRow);
      }
      currentRow = [];
      currentField = '';
    } else {
      currentField += char;
    }
  }

  currentRow.push(currentField.trim());
  if (currentRow.some((f) => f.trim())) {
    rows.push(currentRow);
  }

  return rows;
}

export function parseCSV(text: string, sepKey: string, pageCount: number): string[][] {
  const sep = resolveSep(sepKey);
  return parseFullText(normalizeCsvText(text), sep).map((parts) => {
    while (parts.length < pageCount) {
      parts.push('');
    }
    return parts.slice(0, pageCount);
  });
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

