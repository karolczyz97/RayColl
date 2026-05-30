export const SEPARATORS: Record<string, string> = {
  tab: '\t',
  semicolon: ';',
  comma: ',',
  pipe: '|',
};

function normalizeCsvText(text: string): string {
  return text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
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
  if (lines.length === 0) return 2;
  const counts = lines.map((l) => parseCSVLine(l, sep).length);
  const maxCols = Math.max(...counts);
  return Math.max(2, Math.min(5, maxCols));
}

const HEADER_LABELS = [
  'word',
  'phrase',
  'translation',
  'tłumaczenie',
  'example',
  'przykład',
  'english',
  'polish',
  'deutsch',
  'español',
  'french',
  'italian',
  'słowo',
  'fraz',
  'polsk',
  'niemiec',
  'hiszpań',
  'franc',
  'włosk',
  'angiel',
  'front',
  'back',
  'definition',
  'definicja',
  'meaning',
  'note',
  'notatka',
];

function looksLikeHeaderField(field: string): boolean {
  const f = field.toLowerCase().trim().replace(/[^a-ząćęłńóśźż]/g, '');
  if (f.length > 30) return false;
  return HEADER_LABELS.some((label) => f.includes(label));
}

export function detectFirstRowHeader(text: string, sep: string): { isLikelyHeader: boolean; confidence: number } {
  const sepChar = SEPARATORS[sep] ?? sep;
  const lines = normalizeCsvText(text)
    .split('\n')
    .filter((l) => l.trim());

  if (lines.length === 0) return { isLikelyHeader: false, confidence: 0 };

  const firstRow = parseCSVLine(lines[0], sepChar);
  const otherRows = lines.slice(1).map((l) => parseCSVLine(l, sepChar));

  let score = 0;

  // Heuristic 1: first row contains known header labels
  const headerFields = firstRow.filter(looksLikeHeaderField);
  if (headerFields.length > 0) {
    score += (headerFields.length / firstRow.length) * 2;
  }

  // Heuristic 2: first row has similar column count to the majority of data rows
  if (otherRows.length > 0) {
    const avgCols = otherRows.reduce((sum, row) => sum + row.length, 0) / otherRows.length;
    if (Math.abs(firstRow.length - avgCols) <= 0.5) {
      score += 0.5;
    }
  }

  // Heuristic 3: all fields in the first row are short (likely labels, not sentences)
  const allShort = firstRow.every((f) => f.trim().split(/\s+/).length <= 3);
  if (allShort && firstRow.length > 1) {
    score += 0.5;
  }

  const confidence = Math.min(1, score / 2);
  return { isLikelyHeader: confidence >= 0.75, confidence };
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

