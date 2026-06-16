import * as DocumentPicker from 'expo-document-picker';
import { readAssetText } from '@/utils/fileReader';
import {
  detectFirstRowHeader,
  detectPageCount,
  detectSeparator,
} from '@/import/importParser';
import { createNewSrsState } from '@/srs/srsEngine';
import { uid } from '@/utils/id';
import { getPreviewRows } from './importDraftHelpers';
import type { Flashcard } from '@/types/models';

export const IMPORT_LINE_LIMIT = 500;

/** Resolves the separator key into the literal separator the parser consumes. */
export function getActiveSepValue(key: string, custom: string): string {
  if (key === 'custom') return custom || ',';
  return key;
}

export function limitImportLines(text: string, maxLines: number): {
  safeText: string;
  wasLimited: boolean;
} {
  const lines = text.split('\n');
  const wasLimited = lines.length > maxLines;
  return {
    safeText: wasLimited ? lines.slice(0, maxLines).join('\n') : text,
    wasLimited,
  };
}

export function detectTextProperties(text: string) {
  const detectedSep = detectSeparator(text);
  const headerDetection = detectFirstRowHeader(text, detectedSep);
  const detectedCount = detectPageCount(
    text,
    detectedSep,
    headerDetection.isLikelyHeader,
  );
  return { detectedSep, headerDetection, detectedCount };
}

export function buildCardsFromText(
  text: string,
  sepKey: string,
  pageCount: number,
  skipHeader: boolean,
  existingCards: Flashcard[],
): Flashcard[] {
  if (!text.trim()) {
    return [];
  }

  const parsedRows = getPreviewRows(text, sepKey, pageCount, skipHeader);

  const isSame =
    existingCards.length === parsedRows.length &&
    existingCards.every((card, index) => {
      const row = parsedRows[index];
      return (
        card.pages.length === row.length &&
        card.pages.every((page, pageIndex) => page === row[pageIndex])
      );
    });

  if (isSame) {
    return existingCards;
  }

  return parsedRows.map((row, index) => ({
    id: existingCards[index]?.id || uid(),
    pages: row,
    srsState: createNewSrsState(),
    contentUpdatedAt: existingCards[index] ? existingCards[index].contentUpdatedAt : 0,
    srsUpdatedAt: existingCards[index] ? existingCards[index].srsUpdatedAt : 0,
  }));
}

export async function pickAndReadFile(): Promise<{
  content: string;
  baseName?: string;
} | null> {
  const result = await DocumentPicker.getDocumentAsync({
    type: [
      'text/csv',
      'text/plain',
      'text/comma-separated-values',
      'text/tab-separated-values',
    ],
    copyToCacheDirectory: true,
  });

  if (!result.canceled && result.assets?.length) {
    const asset = result.assets[0];
    const fileContent = await readAssetText(asset);

    if (fileContent) {
      const baseName = asset.name?.replace(/\.[^/.]+$/, '').trim();
      return { content: fileContent, baseName: baseName || undefined };
    }
  }

  return null;
}
