export const SEPARATORS: Record<string, string> = {
  tab: '\t',
  semicolon: ';',
  comma: ',',
  pipe: '|',
};

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
  const firstLine = text.split('\n').find((l) => l.trim()) || '';
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
  const lines = text.split('\n').filter((l) => l.trim());
  if (lines.length === 0) return 2;
  const counts = lines.map((l) => parseCSVLine(l, sep).length);
  const maxCols = Math.max(...counts);
  return Math.max(2, Math.min(5, maxCols));
}

export function detectLangFromHeader(header: string): string {
  const h = header.toLowerCase().trim();
  if (h.includes('word') || h.includes('phrase') || h.includes('english') || h.includes('angiel') || h === 'en') {
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

export function parseCSV(text: string, sepKey: string, pageCount: number): string[][] {
  const sep = SEPARATORS[sepKey] || ';';
  return text
    .split('\n')
    .filter((l) => l.trim())
    .map((line) => {
      const parts = parseCSVLine(line, sep);
      while (parts.length < pageCount) {
        parts.push('');
      }
      return parts.slice(0, pageCount);
    });
}
