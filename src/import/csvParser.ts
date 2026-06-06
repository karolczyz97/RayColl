import { padArray } from '@/utils/array';

export const SEPARATORS: Record<string, string> = {
  tab: '\t',
  semicolon: ';',
  comma: ',',
  pipe: '|',
};

export function normalizeImportCell(value: string): string {
  return value.trim();
}

export function normalizeCsvText(text: string): string {
  return text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
}

export function readNextField(text: string, startPos: number, sep: string): { field: string; nextPos: number } {
  let field = '';
  let inQuotes = false;
  let i = startPos;
  for (; i < text.length; i++) {
    const char = text[i];
    if (char === '"') {
      if (inQuotes && text[i + 1] === '"') {
        field += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (!inQuotes && sep && text.startsWith(sep, i)) {
      break;
    } else if (char === '\n' && !inQuotes) {
      break;
    } else {
      field += char;
    }
  }
  return { field, nextPos: i };
}

export function resolveSep(keyOrChar: string): string {
  return SEPARATORS[keyOrChar] ?? keyOrChar;
}

export function parseFullText(text: string, sep: string): string[][] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  // Separators may be multiple characters (e.g. a custom "::"); advance past the
  // whole match, not a single char. Falls back to 1 to avoid a zero-length step.
  const sepLen = sep.length || 1;
  let pos = 0;

  while (pos < text.length) {
    const { field, nextPos } = readNextField(text, pos, sep);
    currentRow.push(normalizeImportCell(field));

    if (nextPos >= text.length) break;

    if (text[nextPos] === '\n') {
      if (currentRow.some((f) => f.trim())) {
        rows.push(currentRow);
      }
      currentRow = [];
      pos = nextPos + 1;
    } else {
      pos = nextPos + sepLen;
    }
  }

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
