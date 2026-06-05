import { SEPARATORS, normalizeCsvText, parseFullText } from './csvParser';

export function detectSeparator(text: string): string {
  const normalized = normalizeCsvText(text);

  let bestKey = 'semicolon';
  let bestScore = 0;

  for (const [key, sep] of Object.entries(SEPARATORS)) {
    const rows = parseFullText(normalized, sep)
      .filter((row) => row.some((f) => f.trim()))
      .slice(0, 5);

    if (rows.length === 0) continue;

    let totalCount = 0;
    let consistentLines = 0;

    for (const row of rows) {
      const count = row.length - 1;
      if (count > 0) {
        totalCount += count;
        consistentLines++;
      }
    }

    const score = totalCount * (consistentLines / rows.length);
    if (score > bestScore) {
      bestScore = score;
      bestKey = key;
    }
  }

  return bestScore > 0 ? bestKey : 'semicolon';
}
