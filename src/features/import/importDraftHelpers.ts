import { parseCSV, parseCSVRaw, serializeCSV } from '../../import/importParser';

function normalizeRow(row: string[], pageCount: number): string[] {
  const nextRow = row.slice(0, pageCount);
  while (nextRow.length < pageCount) {
    nextRow.push('');
  }
  return nextRow;
}

export function getHeaderRowFromText(text: string, sepKey: string, pageCount: number): string[] {
  const rawRows = parseCSVRaw(text, sepKey);
  const firstRow = rawRows[0] ?? [];
  return normalizeRow(
    firstRow.map((field) => field.replace(/"/g, '').trim()),
    pageCount,
  );
}

export function getPreviewRows(
  text: string,
  sepKey: string,
  pageCount: number,
  firstRowIsHeader: boolean,
): string[][] {
  const rows = parseCSV(text, sepKey, pageCount);
  return firstRowIsHeader ? rows.slice(1) : rows;
}

export function serializeImportSourceText(
  rows: string[][],
  sepKey: string,
  pageCount: number,
  firstRowIsHeader: boolean,
  pageNames: string[],
): string {
  const normalizedRows = rows.map((row) => normalizeRow(row, pageCount));
  const outputRows = firstRowIsHeader
    ? [normalizeRow(pageNames, pageCount), ...normalizedRows]
    : normalizedRows;
  return serializeCSV(outputRows, sepKey);
}

export function replaceHeaderRowInText(
  text: string,
  sepKey: string,
  pageCount: number,
  pageNames: string[],
): string {
  const rawRows = parseCSVRaw(text, sepKey);
  const normalizedHeader = normalizeRow(pageNames, pageCount);
  const bodyRows = rawRows.length > 0 ? rawRows.slice(1) : [];
  return serializeCSV([normalizedHeader, ...bodyRows], sepKey);
}
