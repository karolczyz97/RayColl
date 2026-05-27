import { useCallback, useEffect, useMemo, useState } from 'react';
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
  serializeCSV,
} from '../../import/importParser';
import { createNewSrsState } from '../../srs/srsEngine';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import { useFlashcardListEditing } from '../flashcards/useFlashcardListEditing';

function createCardId() {
  return `import-${Math.random().toString(36).slice(2, 9)}`;
}

export function useImportDeckDraft() {
  const store = useFlashcardStore();
  const [name, setName] = useState('');
  const [rawText, setRawText] = useState('');
  const [sepKey, setSepKey] = useState('semicolon');
  const [pageCount, setPageCount] = useState(2);
  const [pageNames, setPageNames] = useState(['Phrase', 'Tlumaczenie', '', '', '']);
  const [pageLangs, setPageLangs] = useState(['en-US', 'pl-PL', '', '', '']);
  const [cards, setCards] = useState<Flashcard[]>([]);
  const [importError, setImportError] = useState('');
  const [isImporting, setIsImporting] = useState(false);
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
        setRawText(serializeCSV(updatedCards.map((card) => card.pages), sepKey));
        return updatedCards;
      });
    },
    onDeleteCard: (cardId) => {
      setCards((prevCards) => {
        const updatedCards = prevCards.filter((card) => card.id !== cardId);
        setRawText(serializeCSV(updatedCards.map((card) => card.pages), sepKey));
        return updatedCards;
      });
    },
  });

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
    rebuildPreviewFromText(debouncedRawText, sepKey, pageCount);
  }, [debouncedRawText, pageCount, rebuildPreviewFromText, sepKey]);

  const handleTextChange = useCallback((text: string) => {
    setImportError('');
    setRawText(text);

    if (!text.trim()) {
      setCards([]);
      return;
    }

    const detectedSep = detectSeparator(text);
    setSepKey(detectedSep);

    const separator = SEPARATORS[detectedSep as keyof typeof SEPARATORS] || ';';
    const detectedCount = detectPageCount(text, separator);
    setPageCount(detectedCount);

    const firstLine = text.split('\n')[0] || '';
    const parts = firstLine.split(separator);
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
      return;
    }
  }, []);

  const handleSepKeyChange = useCallback(
    (newSep: string) => {
      setSepKey(newSep);
      rebuildPreviewFromText(rawText, newSep, pageCount);
    },
    [pageCount, rawText, rebuildPreviewFromText],
  );

  const handlePageCountChange = useCallback(
    (count: number) => {
      setPageCount(count);
      rebuildPreviewFromText(rawText, sepKey, count);
    },
    [rawText, rebuildPreviewFromText, sepKey],
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
          setRawText(fileContent);

          const detectedSep = detectSeparator(fileContent);
          const separator = SEPARATORS[detectedSep as keyof typeof SEPARATORS] || ';';
          const detectedCount = detectPageCount(fileContent, separator);

          setSepKey(detectedSep);
          setPageCount(detectedCount);
          rebuildPreviewFromText(fileContent, detectedSep, detectedCount);

          const firstLine = fileContent.split('\n')[0] || '';
          const parts = firstLine.split(separator);
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
    } finally {
      setIsImporting(false);
    }
  }, [cards, name, pageCount, pageLangs, pageNames, store]);

  const previewGroup = useMemo<FlashcardGroup>(
    () => ({
      id: 'import-preview',
      name: name || 'Import Preview',
      cards: [],
      activeModeId: '',
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
    handlePickFile,
    startEdit,
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
    submitImport,
    previewGroup,
  };
}
