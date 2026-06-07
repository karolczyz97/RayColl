import { useCallback, useMemo, useReducer, useRef, useState } from 'react';
import { swapElements } from '@/utils/array';
import { safeBack } from '@/utils/navigation';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useDebounce } from '@/hooks/useDebounce';
import { useSyncedRef } from '@/hooks/useSyncedRef';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { useFlashcardListEditing } from '@/features/flashcards/useFlashcardListEditing';
import { DEFAULT_STUDY_FILTER } from '@/store/storeDataNormalization';
import { MAX_STORED_PAGE_COUNT, MAX_VISIBLE_PAGE_COUNT, MIN_PAGE_COUNT } from '@/constants/pages';
import {
  getPreviewRows,
  replaceHeaderRowInText,
  serializeImportSourceText,
} from './importDraftHelpers';
import { getActiveSepValue, pickAndReadFile } from './importDraftUtils';
import { useImportTextSync } from './useImportTextSync';
import {
  importDraftReducer,
  createInitialDraftState,
} from './importDraftReducer';

export function useImportDeckDraft() {
  const store = useFlashcardStore();

  // --- State ---------------------------------------------------------------
  const [state, dispatch] = useReducer(importDraftReducer, null, createInitialDraftState);
  const draftRef = useSyncedRef(state);

  const {
    name,
    rawText,
    sepKey,
    customSep,
    pageCount,
    pageNames,
    pageLangs,
    cards,
    importError,
    errorDismissed,
    firstRowIsHeader,
    rawColumnCount,
  } = state;

  const [isImporting, setIsImporting] = useState(false);
  const [nameTouched, setNameTouched] = useState(false);
  const [sourceTouched, setSourceTouched] = useState(false);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  // --- Refs: synced with state for immediate read-after-dispatch ------------
  const firstRowIsHeaderRef = useSyncedRef(firstRowIsHeader);
  const pageNamesRef = useSyncedRef(pageNames);
  const pageLangsRef = useSyncedRef(pageLangs);

  // --- Refs (non-rendered state) -------------------------------------------
  const headerSettingTouchedRef = useRef(false);
  const lastAppliedHeaderKeyRef = useRef<string | null>(null);
  const preHeaderPageNamesRef = useRef<string[] | null>(null);
  const isPasteRef = useRef(false);
  const sepTouchedRef = useRef(false);
  const lastSyncedKeyRef = useRef('');

  // --- Derived -------------------------------------------------------------
  const debouncedRawText = useDebounce(rawText, 300);

  // --- Card - text sync ----------------------------------------------------
  const syncRawTextFromCards = useCallback(
    (updatedCards: Flashcard[]) => {
      const s = draftRef.current;
      dispatch({
        type: 'SET_RAW_TEXT',
        value: serializeImportSourceText(
          updatedCards.map((card) => card.pages),
          getActiveSepValue(s.sepKey, s.customSep),
          s.pageCount,
          firstRowIsHeaderRef.current,
          pageNamesRef.current,
        ),
      });
    },
    [dispatch, draftRef, firstRowIsHeaderRef, pageNamesRef],
  );

  // --- Flashcard list editing ----------------------------------------------
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
      const updatedCards = draftRef.current.cards.map((card) =>
        card.id === cardId ? { ...card, pages: [...pages] } : card,
      );
      syncRawTextFromCards(updatedCards);
      dispatch({ type: 'SET_CARDS', value: updatedCards });
    },
    onDeleteCard: (cardId) => {
      const updatedCards = draftRef.current.cards.filter((card) => card.id !== cardId);
      syncRawTextFromCards(updatedCards);
      dispatch({ type: 'SET_CARDS', value: updatedCards });
    },
  });

  // --- Text sync hook ------------------------------------------------------
  const { syncDraftFromText, applyDetectedText, syncHeaderConfigFromText } =
    useImportTextSync({
      debouncedRawText,
      sepKey,
      customSep,
      pageCount,
      dispatch,
      draftRef,
      firstRowIsHeaderRef,
      pageNamesRef,
      pageLangsRef,
      headerSettingTouchedRef,
      lastAppliedHeaderKeyRef,
      preHeaderPageNamesRef,
      sepTouchedRef,
      lastSyncedKeyRef,
    });

  // --- Handlers ------------------------------------------------------------
  const handlePaste = useCallback(() => {
    isPasteRef.current = true;
  }, []);

  const handleTextChange = useCallback(
    (text: string) => {
      if (!text.trim()) {
        dispatch({ type: 'SET_RAW_TEXT', value: '' });
        dispatch({ type: 'SET_CARDS', value: [] });
        isPasteRef.current = false;
        sepTouchedRef.current = false;
        return;
      }

      const deltaLines = Math.abs(
        text.split('\n').length - draftRef.current.rawText.split('\n').length,
      );
      const isPaste = isPasteRef.current || deltaLines >= 2;
      isPasteRef.current = false;

      if (isPaste) {
        applyDetectedText(text);
      } else {
        dispatch({ type: 'SET_RAW_TEXT', value: text });
      }
    },
    [applyDetectedText, dispatch, draftRef],
  );

  const handleSepKeyChange = useCallback(
    (newSep: string, newCustomSep?: string) => {
      sepTouchedRef.current = true;
      dispatch({ type: 'SET_SEP_KEY', value: newSep });
      if (newSep !== 'custom') {
        dispatch({ type: 'SET_CUSTOM_SEP', value: '' });
        syncDraftFromText(rawText, newSep, pageCount);
      } else if (newCustomSep !== undefined) {
        dispatch({ type: 'SET_CUSTOM_SEP', value: newCustomSep });
        syncDraftFromText(rawText, newCustomSep || ',', pageCount);
      }
    },
    [dispatch, pageCount, rawText, syncDraftFromText],
  );

  const handlePageCountChange = useCallback(
    (count: number) => {
      const s = draftRef.current;
      const minCount = Math.max(MIN_PAGE_COUNT, s.rawColumnCount);
      const clampedCount = Math.max(minCount, Math.min(MAX_STORED_PAGE_COUNT, count));

      const activeSep = getActiveSepValue(s.sepKey, s.customSep);
      const headerPageCount = Math.min(clampedCount, s.rawColumnCount);
      const nextText =
        firstRowIsHeaderRef.current && s.rawText.trim()
          ? replaceHeaderRowInText(s.rawText, activeSep, headerPageCount, pageNamesRef.current)
          : s.rawText;

      dispatch({ type: 'SET_PAGE_COUNT', value: clampedCount });
      if (nextText !== s.rawText) {
        dispatch({ type: 'SET_RAW_TEXT', value: nextText });
      }
      syncDraftFromText(nextText, activeSep, clampedCount);
    },
    [dispatch, draftRef, firstRowIsHeaderRef, pageNamesRef, syncDraftFromText],
  );

  const handleHeaderToggle = useCallback(
    (value: boolean) => {
      headerSettingTouchedRef.current = true;
      firstRowIsHeaderRef.current = value;
      dispatch({ type: 'SET_FIRST_ROW_IS_HEADER', value });

      const s = draftRef.current;
      const activeSep = getActiveSepValue(s.sepKey, s.customSep);
      if (value && s.rawText.trim()) {
        if (preHeaderPageNamesRef.current === null) {
          preHeaderPageNamesRef.current = [...pageNamesRef.current];
        }
        syncHeaderConfigFromText(s.rawText, activeSep, s.pageCount);
      } else if (!value) {
        lastAppliedHeaderKeyRef.current = null;
        if (preHeaderPageNamesRef.current !== null) {
          const restored = [...preHeaderPageNamesRef.current];
          pageNamesRef.current = restored;
          dispatch({ type: 'SET_PAGE_NAMES', value: restored });
          preHeaderPageNamesRef.current = null;
        }
      }
      syncDraftFromText(s.rawText, activeSep, s.pageCount);
    },
    [dispatch, draftRef, firstRowIsHeaderRef, pageNamesRef, syncDraftFromText, syncHeaderConfigFromText],
  );

  const handlePageNameChange = useCallback(
    (index: number, value: string) => {
      const nextNames = [...pageNamesRef.current];
      nextNames[index] = value;
      pageNamesRef.current = nextNames;
      dispatch({ type: 'SET_PAGE_NAMES', value: nextNames });

      const s = draftRef.current;
      if (!firstRowIsHeaderRef.current || !s.rawText.trim()) {
        return;
      }

      const activeSep = getActiveSepValue(s.sepKey, s.customSep);
      const nextText = replaceHeaderRowInText(s.rawText, activeSep, s.pageCount, nextNames);
      dispatch({ type: 'SET_RAW_TEXT', value: nextText });
      syncDraftFromText(nextText, activeSep, s.pageCount);
    },
    [dispatch, draftRef, firstRowIsHeaderRef, pageNamesRef, syncDraftFromText],
  );

  const handleMovePage = useCallback(
    (index: number, direction: -1 | 1) => {
      const target = index + direction;
      const s = draftRef.current;
      if (target < 0 || target >= s.pageCount) return;

      const nextPageNames = swapElements(pageNamesRef.current, index, target);
      const nextPageLangs = swapElements(pageLangsRef.current, index, target);
      pageNamesRef.current = nextPageNames;
      pageLangsRef.current = nextPageLangs;
      dispatch({ type: 'SET_PAGE_NAMES', value: nextPageNames });
      dispatch({ type: 'SET_PAGE_LANGS', value: nextPageLangs });

      const activeSep = getActiveSepValue(s.sepKey, s.customSep);
      const rawRows = getPreviewRows(s.rawText, activeSep, s.pageCount, false);
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
          s.pageCount,
          firstRowIsHeaderRef.current,
          nextPageNames,
        );
        dispatch({ type: 'SET_RAW_TEXT', value: nextText });
        syncDraftFromText(nextText, activeSep, s.pageCount);
      }
    },
    [dispatch, draftRef, firstRowIsHeaderRef, pageNamesRef, pageLangsRef, syncDraftFromText],
  );

  const handlePickFile = useCallback(async () => {
    dispatch({ type: 'SET_IMPORT_ERROR', value: '' });
    sepTouchedRef.current = false;

    try {
      const result = await pickAndReadFile();
      if (result) {
        applyDetectedText(result.content, result.baseName);
      }
    } catch (err: unknown) {
      dispatch({
        type: 'SET_IMPORT_ERROR',
        value: err instanceof Error ? err.message : 'Failed to pick or read file',
      });
    }
  }, [applyDetectedText, dispatch]);

  const isImportBlocked = importError.startsWith('import.err.');
  const hasSourceContent = rawText.trim().length > 0 && cards.length > 0;
  const showNameRequiredError = (nameTouched || submitAttempted) && !name.trim();
  const showSourceRequiredError = (sourceTouched || submitAttempted) && !hasSourceContent;

  const submitImport = useCallback(async () => {
    setSubmitAttempted(true);
    if (!name.trim() || !hasSourceContent) return;
    if (isImportBlocked) return;

    setIsImporting(true);
    dispatch({ type: 'SET_IMPORT_ERROR', value: '' });

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

      dispatch({ type: 'SET_IMPORT_ERROR', value: result.error });
    } catch (err) {
      dispatch({
        type: 'SET_IMPORT_ERROR',
        value: err instanceof Error ? err.message : 'Import failed.',
      });
    } finally {
      setIsImporting(false);
    }
  }, [cards, dispatch, hasSourceContent, isImportBlocked, name, pageCount, pageLangs, pageNames, store]);

  const previewGroup = useMemo<FlashcardGroup>(
    () => ({
      id: 'import-preview',
      name: name || 'Import Preview',
      cards: [],
      activeModeId: 'classic',
      studyFilter: DEFAULT_STUDY_FILTER,
      pageLanguages: pageLangs.slice(0, pageCount),
      pageNames: pageNames.slice(0, pageCount),
      activePageCount: Math.min(pageCount, MAX_VISIBLE_PAGE_COUNT),
    }),
    [name, pageCount, pageLangs, pageNames],
  );

  // --- Setter wrappers (compatible with external consumers) ----------------
  const setName = useCallback(
    (value: string) => dispatch({ type: 'SET_NAME', value }),
    [dispatch],
  );
  const handleNameBlur = useCallback(() => {
    setNameTouched(true);
  }, []);
  const handleSourceBlur = useCallback(() => {
    setSourceTouched(true);
  }, []);
  const setPageLangs = useCallback(
    (value: string[] | ((prev: string[]) => string[])) => {
      const resolved =
        typeof value === 'function' ? value(pageLangsRef.current) : value;
      pageLangsRef.current = resolved;
      dispatch({ type: 'SET_PAGE_LANGS', value: resolved });
    },
    [dispatch, pageLangsRef],
  );
  const setImportError = useCallback(
    (value: string) => dispatch({ type: 'SET_IMPORT_ERROR', value }),
    [dispatch],
  );
  const dismissImportError = useCallback(
    () => dispatch({ type: 'DISMISS_IMPORT_ERROR' }),
    [dispatch],
  );

  // --- Return (compatible with prior interface) ----------------------------
  return {
    name,
    setName,
    handleNameBlur,
    showNameRequiredError,
    rawText,
    handleSourceBlur,
    showSourceRequiredError,
    sepKey,
    customSep,
    pageCount,
    pageNames,
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
    errorDismissed,
    dismissImportError,
    isImporting,
    isImportBlocked,
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
    rawColumnCount,
  };
}
