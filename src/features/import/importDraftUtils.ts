import { Platform } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import {
  detectFirstRowHeader,
  detectPageCount,
  detectSeparator,
} from '@/import/importParser';
import { createNewSrsState } from '@/srs/srsEngine';
import { getPreviewRows } from './importDraftHelpers';
import type { Flashcard } from '@/types/models';

export const IMPORT_LINE_LIMIT = 500;

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
    id: existingCards[index]?.id || `import-${Math.random().toString(36).slice(2, 9)}`,
    pages: row,
    srsState: createNewSrsState(),
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
    const fileContent =
      Platform.OS === 'web' && asset.file
        ? await asset.file.text()
        : await (await fetch(asset.uri)).text();

    if (fileContent) {
      const baseName = asset.name?.replace(/\.[^/.]+$/, '').trim();
      return { content: fileContent, baseName: baseName || undefined };
    }
  }

  return null;
}
