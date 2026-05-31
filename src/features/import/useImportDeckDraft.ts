import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Platform } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import { safeBack } from '../../utils/navigation';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useDebounce } from '../../hooks/useDebounce';
import {
  SEPARATORS,
  detectFirstRowHeader,
  detectLangFromHeader,
  detectPageCount,
  detectSeparator,
} from '../../import/importParser';
import { createNewSrsState } from '../../srs/srsEngine';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import { useFlashcardListEditing } from '../flashcards/useFlashcardListEditing';
import { DEFAULT_STUDY_FILTER } from '../../store/storeDataNormalization';
import {
  getHeaderRowFromText,
  getPreviewRows,
  replaceHeaderRowInText,
  serializeImportSourceText,
} from './importDraftHelpers';

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
  const [pageNames, setPageNames] = useState(['', '', '', '', '']);
  const [pageLangs, setPageLangs] = useState(['en-US', 'pl-PL', '', '', '']);
  const [cards, setCards] = useState<Flashcard[]>([]);
  const [importError, setImportError] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [firstRowIsHeader, setFirstRowIsHeader] = useState(false);
  const firstRowIsHeaderRef = useRef(firstRowIsHeader);
  const headerSettingTouchedRef = useRef(false);
  const lastAppliedHeaderKeyRef = useRef<string | null>(null);
  const pageNamesRef = useRef(pageNames);
  const preHeaderPageNamesRef = useRef<string[] | null>(null);
  const isPasteRef = useRef(false);
  const rawTextRef = useRef(rawText);
  const debouncedRawText = useDebounce(rawText, 300);

  const getActiveSepValue = useCallback((key: string, custom: string): string => {
    if (key === 'custom') return custom || ',';
    return key;
  }, []);

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
        setRawText(
          serializeImportSourceText(
            updatedCards.map((card) => card.pages),
            getActiveSepValue(sepKey, customSep),
            pageCount,
            firstRowIsHeaderRef.current,
            pageNames,
          ),
        );
        return updatedCards;
      });
    },
    onDeleteCard: (cardId) => {
      setCards((prevCards) => {
        const updatedCards = prevCards.filter((card) => card.id !== cardId);
        setRawText(
          serializeImportSourceText(
            updatedCards.map((card) => card.pages),
            getActiveSepValue(sepKey, customSep),
            pageCount,
            firstRowIsHeaderRef.current,
            pageNames,
          ),
        );
        return updatedCards;
      });
    },
  });

  const rebuildPreviewFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number, skipHeader: boolean) => {
      if (!text.trim()) {
        setCards([]);
        return;
      }

      const parsedRows = getPreviewRows(text, currentSepKey, currentPageCount, skipHeader);
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

  const syncHeaderConfigFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number) => {
      const headerRow = getHeaderRowFromText(text, currentSepKey, currentPageCount);
      const headerKey = headerRow.join('\u0000');
      if (headerKey === lastAppliedHeaderKeyRef.current) {
        return;
      }

      lastAppliedHeaderKeyRef.current = headerKey;
      setPageNames((prev) => {
        const next = [...prev];
        headerRow.forEach((part, index) => {
          if (index < 5) {
            next[index] = part;
          }
        });
        return next;
      });
      setPageLangs((prev) => {
        const next = [...prev];
        headerRow.forEach((part, index) => {
          if (index < 5) {
            next[index] = detectLangFromHeader(part);
          }
        });
        return next;
      });
    },
    [],
  );

  const syncDraftFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number) => {
      if (!text.trim()) {
        lastAppliedHeaderKeyRef.current = null;
        setCards([]);
        return;
      }

      const detection = detectFirstRowHeader(text, currentSepKey, currentPageCount);
      const effectiveFirstRowIsHeader = headerSettingTouchedRef.current
        ? firstRowIsHeaderRef.current
        : detection.isLikelyHeader;

      if (
        !headerSettingTouchedRef.current &&
        effectiveFirstRowIsHeader !== firstRowIsHeaderRef.current
      ) {
        firstRowIsHeaderRef.current = effectiveFirstRowIsHeader;
        setFirstRowIsHeader(effectiveFirstRowIsHeader);
      }

      if (effectiveFirstRowIsHeader) {
        if (preHeaderPageNamesRef.current === null) {
          preHeaderPageNamesRef.current = [...pageNamesRef.current];
        }
        syncHeaderConfigFromText(text, currentSepKey, currentPageCount);
      } else {
        lastAppliedHeaderKeyRef.current = null;
      }

      rebuildPreviewFromText(text, currentSepKey, currentPageCount, effectiveFirstRowIsHeader);
    },
    [rebuildPreviewFromText, syncHeaderConfigFromText],
  );

  const applyDetectedText = useCallback(
    (text: string, nextName?: string) => {
      const lines = text.split('\n');
      const limited = lines.length > IMPORT_LINE_LIMIT;
      const safeText = limited ? lines.slice(0, IMPORT_LINE_LIMIT).join('\n') : text;

      if (nextName) {
        setName(nextName);
      }

      preHeaderPageNamesRef.current = null;
      setImportError(limited ? 'import.err.too_many_lines' : '');
      setRawText(safeText);

      if (!safeText.trim()) {
        setCards([]);
        return;
      }

      const detectedSep = detectSeparator(safeText);
      const separator = SEPARATORS[detectedSep] || ';';
      const detectedCount = detectPageCount(safeText, separator);

      setSepKey(detectedSep);
      setCustomSep('');
      setPageCount(detectedCount);
      syncDraftFromText(safeText, detectedSep, detectedCount);
    },
    [syncDraftFromText],
  );

  useEffect(() => {
    firstRowIsHeaderRef.current = firstRowIsHeader;
  }, [firstRowIsHeader]);

  useEffect(() => {
    pageNamesRef.current = pageNames;
  }, [pageNames]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- debounced preview intentionally syncs local derived draft state
    syncDraftFromText(debouncedRawText, getActiveSepValue(sepKey, customSep), pageCount);
  }, [customSep, debouncedRawText, getActiveSepValue, pageCount, sepKey, syncDraftFromText]);

  useEffect(() => {
    rawTextRef.current = rawText;
  }, [rawText]);

  const handlePaste = useCallback(() => {
    isPasteRef.current = true;
  }, []);

  const handleTextChange = useCallback(
    (text: string) => {
      if (!text.trim()) {
        setRawText('');
        setCards([]);
        isPasteRef.current = false;
        return;
      }

      // Detect paste using explicit paste event flag or significant text length jump
      const isPaste = isPasteRef.current || text.length - rawTextRef.current.length > 10;
      isPasteRef.current = false;

      if (isPaste) {
        applyDetectedText(text);
      } else {
        setRawText(text);
      }
    },
    [applyDetectedText],
  );

  const handleSepKeyChange = useCallback(
    (newSep: string, newCustomSep?: string) => {
      setSepKey(newSep);
      if (newSep !== 'custom') {
        setCustomSep('');
        syncDraftFromText(rawText, newSep, pageCount);
      } else if (newCustomSep !== undefined) {
        setCustomSep(newCustomSep);
        syncDraftFromText(rawText, newCustomSep || ',', pageCount);
      }
    },
    [pageCount, rawText, syncDraftFromText],
  );

  const handlePageCountChange = useCallback(
    (count: number) => {
      const activeSep = getActiveSepValue(sepKey, customSep);
      const nextText =
        firstRowIsHeaderRef.current && rawText.trim()
          ? replaceHeaderRowInText(rawText, activeSep, count, pageNames)
          : rawText;

      setPageCount(count);
      if (nextText !== rawText) {
        setRawText(nextText);
      }
      syncDraftFromText(nextText, activeSep, count);
    },
    [customSep, getActiveSepValue, pageNames, rawText, sepKey, syncDraftFromText],
  );

  const handleHeaderToggle = useCallback(
    (value: boolean) => {
      headerSettingTouchedRef.current = true;
      firstRowIsHeaderRef.current = value;
      setFirstRowIsHeader(value);

      const activeSep = getActiveSepValue(sepKey, customSep);
      if (value && rawText.trim()) {
        if (preHeaderPageNamesRef.current === null) {
          preHeaderPageNamesRef.current = [...pageNamesRef.current];
        }
        syncHeaderConfigFromText(rawText, activeSep, pageCount);
      } else if (!value) {
        lastAppliedHeaderKeyRef.current = null;
        if (preHeaderPageNamesRef.current !== null) {
          setPageNames([...preHeaderPageNamesRef.current]);
          preHeaderPageNamesRef.current = null;
        }
      }
      syncDraftFromText(rawText, activeSep, pageCount);
    },
    [
      customSep,
      getActiveSepValue,
      pageCount,
      rawText,
      sepKey,
      syncDraftFromText,
      syncHeaderConfigFromText,
    ],
  );

  const handlePageNameChange = useCallback(
    (index: number, value: string) => {
      const nextNames = [...pageNames];
      nextNames[index] = value;
      setPageNames(nextNames);

      if (!firstRowIsHeaderRef.current || !rawText.trim()) {
        return;
      }

      const activeSep = getActiveSepValue(sepKey, customSep);
      const nextText = replaceHeaderRowInText(rawText, activeSep, pageCount, nextNames);
      setRawText(nextText);
      syncDraftFromText(nextText, activeSep, pageCount);
    },
    [customSep, getActiveSepValue, pageCount, pageNames, rawText, sepKey, syncDraftFromText],
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

      const nextPageNames = swap(pageNames);
      const nextPageLangs = swap(pageLangs);
      setPageNames(nextPageNames);
      setPageLangs(nextPageLangs);

      const activeSep = getActiveSepValue(sepKey, customSep);
      const rawRows = getPreviewRows(rawText, activeSep, pageCount, false);
      if (rawRows.length > 0) {
        const swappedRows = rawRows.map((row) => {
          const next = [...row];
          const maxIdx = Math.max(index, target);
          while (next.length <= maxIdx) next.push('');
          [next[index], next[target]] = [next[target], next[index]];
          return next;
        });
        const nextText = serializeImportSourceText(
          firstRowIsHeaderRef.current ? swappedRows.slice(1) : swappedRows,
          activeSep,
          pageCount,
          firstRowIsHeaderRef.current,
          nextPageNames,
        );
        setRawText(nextText);
        syncDraftFromText(nextText, activeSep, pageCount);
      }
    },
    [customSep, getActiveSepValue, pageCount, pageLangs, pageNames, rawText, sepKey, syncDraftFromText],
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
          const baseName = asset.name?.replace(/\.[^/.]+$/, '').trim();
          applyDetectedText(fileContent, baseName || undefined);
        }
      }
    } catch (err: unknown) {
      setImportError(err instanceof Error ? err.message : 'Failed to pick or read file');
    }
  }, [applyDetectedText]);

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
        safeBack();
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
    firstRowIsHeader,
    handleTextChange,
    handleSepKeyChange,
    handlePageCountChange,
    handleHeaderToggle,
    handlePageNameChange,
    handleMovePage,
    handlePickFile,
    handlePaste,
    startEdit,
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
    submitImport,
    previewGroup,
  };
}
