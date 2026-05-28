import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Platform } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import { router } from 'expo-router';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useDebounce } from '../../hooks/useDebounce';
import {
  SEPARATORS,
  detectLangFromHeader,
  detectPageCount,
  detectSeparator,
  parseCSV,
  parseCSVRaw,
  serializeCSV,
} from '../../import/importParser';
import { createNewSrsState } from '../../srs/srsEngine';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import { useFlashcardListEditing } from '../flashcards/useFlashcardListEditing';
import { DEFAULT_STUDY_FILTER } from '../../store/storeDataNormalization';

function createCardId() {
  return `import-${Math.random().toString(36).slice(2, 9)}`;
}

const IMPORT_LINE_LIMIT = 500;

export function useImportDeckDraft() {
  const store = useFlashcardStore();
  const [name, setName] = useState('');
  const [rawText, setRawText] = useState('');
  const [sepKey, setSepKey] = useState('semicolon');
  const [customSep, setCustomSep] = useState('');
  const [pageCount, setPageCount] = useState(2);
  const [pageNames, setPageNames] = useState(['Phrase', 'Tlumaczenie', '', '', '']);
  const [pageLangs, setPageLangs] = useState(['en-US', 'pl-PL', '', '', '']);
  const [cards, setCards] = useState<Flashcard[]>([]);
  const [importError, setImportError] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const lastDetectedFirstLine = useRef<string | null>(null);
  const debouncedRawText = useDebounce(rawText, 300);
  const {
    editingId,
    editPages,
    setEditPages,
    deleteCardId,
    setDeleteCardId,
    startEdit,
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
  } = useFlashcardListEditing({
    pageCount,
    onSaveCard: (cardId, pages) => {
      setCards((prevCards) => {
        const updatedCards = prevCards.map((card) =>
          card.id === cardId ? { ...card, pages: [...pages] } : card,
        );
        setRawText(serializeCSV(updatedCards.map((card) => card.pages), getActiveSepValue(sepKey, customSep)));
        return updatedCards;
      });
    },
    onDeleteCard: (cardId) => {
      setCards((prevCards) => {
        const updatedCards = prevCards.filter((card) => card.id !== cardId);
        setRawText(serializeCSV(updatedCards.map((card) => card.pages), getActiveSepValue(sepKey, customSep)));
        return updatedCards;
      });
    },
  });

  const getActiveSepValue = useCallback(
    (key: string, custom: string): string => {
      if (key === 'custom') return custom || ',';
      return key;
    },
    [],
  );

  const rebuildPreviewFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number) => {
      if (!text.trim()) {
        setCards([]);
        return;
      }

      const parsedRows = parseCSV(text, currentSepKey, currentPageCount);
      setCards((prevCards) => {
        const isSame =
          prevCards.length === parsedRows.length &&
          prevCards.every((card, index) => {
            const row = parsedRows[index];
            return (
              card.pages.length === row.length &&
              card.pages.every((page, pageIndex) => page === row[pageIndex])
            );
          });

        if (isSame) {
          return prevCards;
        }

        return parsedRows.map((row, index) => ({
          id: prevCards[index]?.id || createCardId(),
          pages: row,
          srsState: createNewSrsState(),
        }));
      });
    },
    [],
  );

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- debounced preview intentionally syncs local derived draft state
    rebuildPreviewFromText(debouncedRawText, getActiveSepValue(sepKey, customSep), pageCount);
  }, [customSep, debouncedRawText, getActiveSepValue, pageCount, rebuildPreviewFromText, sepKey]);

  const handleTextChange = useCallback((text: string) => {
    setImportError('');

    const lines = text.split('\n');
    const limited = lines.length > IMPORT_LINE_LIMIT;
    const safeText = limited ? lines.slice(0, IMPORT_LINE_LIMIT).join('\n') : text;

    if (limited) {
      setImportError('import.err.too_many_lines');
    }

    setRawText(safeText);

    if (!safeText.trim()) {
      setCards([]);
      return;
    }

    const detectedSep = detectSeparator(safeText);
    setSepKey(detectedSep);
    setCustomSep('');

    const separator = SEPARATORS[detectedSep] || ';';
    const detectedCount = detectPageCount(safeText, separator);
    setPageCount(detectedCount);

    const parts = parseCSVRaw(safeText, separator)[0] ?? [];
    const headerKey = parts.join('\u0000');
    if (headerKey !== lastDetectedFirstLine.current) {
      lastDetectedFirstLine.current = headerKey;
      if (parts.length > 0) {
        setPageNames((prev) => {
          const next = [...prev];
          parts.forEach((part, index) => {
            if (index < 5) next[index] = part.replace(/"/g, '').trim();
          });
          return next;
        });
        setPageLangs((prev) => {
          const next = [...prev];
          parts.forEach((part, index) => {
            if (index < 5) next[index] = detectLangFromHeader(part.replace(/"/g, '').trim());
          });
          return next;
        });
      }
    }
  }, []);

  const handleSepKeyChange = useCallback(
    (newSep: string, newCustomSep?: string) => {
      setSepKey(newSep);
      if (newSep !== 'custom') {
        setCustomSep('');
        rebuildPreviewFromText(rawText, newSep, pageCount);
      } else if (newCustomSep !== undefined) {
        setCustomSep(newCustomSep);
        rebuildPreviewFromText(rawText, newCustomSep || ',', pageCount);
      }
    },
    [pageCount, rawText, rebuildPreviewFromText],
  );

  const handlePageCountChange = useCallback(
    (count: number) => {
      setPageCount(count);
      rebuildPreviewFromText(rawText, getActiveSepValue(sepKey, customSep), count);
    },
    [customSep, getActiveSepValue, rawText, rebuildPreviewFromText, sepKey],
  );

  const handleMovePage = useCallback(
    (index: number, direction: -1 | 1) => {
      const target = index + direction;
      if (target < 0 || target >= pageCount) return;

      const swap = <T,>(arr: T[]): T[] => {
        const next = [...arr];
        [next[index], next[target]] = [next[target], next[index]];
        return next;
      };

      setPageNames((prev) => swap(prev));
      setPageLangs((prev) => swap(prev));

      const activeSep = getActiveSepValue(sepKey, customSep);
      const rawRows = parseCSVRaw(rawText, activeSep);
      if (rawRows.length > 0) {
        const swappedRows = rawRows.map((row) => {
          const next = [...row];
          const maxIdx = Math.max(index, target);
          while (next.length <= maxIdx) next.push('');
          [next[index], next[target]] = [next[target], next[index]];
          return next;
        });
        const nextText = serializeCSV(swappedRows, activeSep);
        lastDetectedFirstLine.current = nextText.split('\n')[0] || '';
        setRawText(nextText);
      }
    },
    [customSep, getActiveSepValue, pageCount, rawText, sepKey],
  );

  const handlePickFile = useCallback(async () => {
    setImportError('');

    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['text/csv', 'text/plain', 'text/comma-separated-values', 'text/tab-separated-values'],
        copyToCacheDirectory: true,
      });

      if (!result.canceled && result.assets?.length) {
        const asset = result.assets[0];
        const fileContent =
          Platform.OS === 'web' && asset.file
            ? await asset.file.text()
            : await (await fetch(asset.uri)).text();

        if (fileContent) {
          setImportError('');
          if (asset.name) {
            const lastDot = asset.name.lastIndexOf('.');
            const nameWithoutExt = lastDot !== -1 ? asset.name.substring(0, lastDot) : asset.name;
            setName(nameWithoutExt);
          }
          const fileLines = fileContent.split('\n');
          const fileLimited = fileLines.length > IMPORT_LINE_LIMIT;
          const safeFileContent = fileLimited
            ? fileLines.slice(0, IMPORT_LINE_LIMIT).join('\n')
            : fileContent;
          if (fileLimited) {
            setImportError('import.err.too_many_lines');
          }
          setRawText(safeFileContent);

          const detectedSep = detectSeparator(safeFileContent);
          const separator = SEPARATORS[detectedSep] || ';';
          const detectedCount = detectPageCount(safeFileContent, separator);

          setSepKey(detectedSep);
          setPageCount(detectedCount);
          rebuildPreviewFromText(safeFileContent, detectedSep, detectedCount);

          const parts = parseCSVRaw(safeFileContent, separator)[0] ?? [];
          lastDetectedFirstLine.current = parts.join('\u0000');
          if (parts.length > 0) {
            setPageNames((prev) => {
              const next = [...prev];
              parts.forEach((part, index) => {
                if (index < 5) {
                  next[index] = part.replace(/"/g, '').trim();
                }
              });
              return next;
            });
            setPageLangs((prev) => {
              const next = [...prev];
              parts.forEach((part, index) => {
                if (index < 5) {
                  next[index] = detectLangFromHeader(part.replace(/"/g, '').trim());
                }
              });
              return next;
            });
          }
        }
      }
    } catch (err: unknown) {
      setImportError(err instanceof Error ? err.message : 'Failed to pick or read file');
    }
  }, [rebuildPreviewFromText]);

  const submitImport = useCallback(async () => {
    if (!name.trim()) return;

    setIsImporting(true);
    setImportError('');

    try {
      const result = await store.importDeck({
        name,
        languages: pageLangs.slice(0, pageCount),
        pageNames: pageNames.slice(0, pageCount),
        cards: cards.map((card) => ({ pages: card.pages })),
      });

      if (result.ok) {
        router.back();
        return;
      }

      setImportError(result.error);
    } catch (err) {
      setImportError(err instanceof Error ? err.message : 'Import failed.');
    } finally {
      setIsImporting(false);
    }
  }, [cards, name, pageCount, pageLangs, pageNames, store]);

  const previewGroup = useMemo<FlashcardGroup>(
    () => ({
      id: 'import-preview',
      name: name || 'Import Preview',
      cards: [],
      activeModeId: 'classic',
      studyFilter: DEFAULT_STUDY_FILTER,
      pageLanguages: pageLangs.slice(0, pageCount),
      pageNames: pageNames.slice(0, pageCount),
      activePageCount: pageCount,
    }),
    [name, pageCount, pageLangs, pageNames],
  );

  return {
    name,
    setName,
    rawText,
    setRawText,
    sepKey,
    setSepKey,
    customSep,
    setCustomSep,
    pageCount,
    setPageCount,
    pageNames,
    setPageNames,
    pageLangs,
    setPageLangs,
    cards,
    editingId,
    editPages,
    setEditPages,
    deleteCardId,
    setDeleteCardId,
    importError,
    setImportError,
    isImporting,
    handleTextChange,
    handleSepKeyChange,
    handlePageCountChange,
    handleMovePage,
    handlePickFile,
    startEdit,
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
    submitImport,
    previewGroup,
  };
}
