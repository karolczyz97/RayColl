import { useCallback, useEffect } from 'react';
import type { MutableRefObject } from 'react';
import {
  detectFirstRowHeader,
  detectLangFromHeader,
  detectPageCount,
} from '@/import/importParser';
import { MAX_STORED_PAGE_COUNT, MIN_PAGE_COUNT } from '@/constants/pages';
import { getHeaderRowFromText } from './importDraftHelpers';
import {
  buildCardsFromText,
  detectTextProperties,
  getActiveSepValue,
  IMPORT_LINE_LIMIT,
  limitImportLines,
} from './importDraftUtils';
import type { ImportDraftState, DraftAction } from './importDraftReducer';

// Joins header fields into a collision-proof comparison key. A control char is
// used because it cannot appear in a normalized (de-quoted, single-row) header.
const HEADER_KEY_SEP = String.fromCharCode(0);

interface UseImportTextSyncParams {
  // Reactive source values
  debouncedRawText: string;
  sepKey: string;
  customSep: string;
  pageCount: number;
  // Reducer dispatch + latest state ref
  dispatch: (action: DraftAction) => void;
  draftRef: MutableRefObject<ImportDraftState>;
  // Individual refs for values that need immediate read-after-dispatch
  firstRowIsHeaderRef: MutableRefObject<boolean>;
  pageNamesRef: MutableRefObject<string[]>;
  pageLangsRef: MutableRefObject<string[]>;
  // Mutable refs shared with the orchestrator
  headerSettingTouchedRef: MutableRefObject<boolean>;
  lastAppliedHeaderKeyRef: MutableRefObject<string | null>;
  preHeaderPageNamesRef: MutableRefObject<string[] | null>;
  sepTouchedRef: MutableRefObject<boolean>;
  lastSyncedKeyRef: MutableRefObject<string>;
}

/**
 * Owns the text -> draft reconciliation: rebuilding the card preview, syncing
 * header config, ingesting freshly pasted/loaded text, and the debounced
 * re-sync while the user types. Kept separate from {@link useImportDeckDraft}
 * so the fragile cascade lives in one focused, reviewable unit.
 */
export function useImportTextSync({
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
}: UseImportTextSyncParams) {
  const rebuildPreviewFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number, skipHeader: boolean) => {
      const newCards = buildCardsFromText(
        text,
        currentSepKey,
        currentPageCount,
        skipHeader,
        draftRef.current.cards,
      );
      dispatch({ type: 'SET_CARDS', value: newCards });
    },
    [dispatch, draftRef],
  );

  const syncHeaderConfigFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number) => {
      const headerRow = getHeaderRowFromText(text, currentSepKey, currentPageCount);
      const headerKey = headerRow.join(HEADER_KEY_SEP);
      if (headerKey === lastAppliedHeaderKeyRef.current) {
        return;
      }

      lastAppliedHeaderKeyRef.current = headerKey;

      const nextNames = [...pageNamesRef.current];
      headerRow.forEach((part, index) => {
        if (index < MAX_STORED_PAGE_COUNT) {
          nextNames[index] = part;
        }
      });
      dispatch({ type: 'SET_PAGE_NAMES', value: nextNames });

      const nextLangs = [...pageLangsRef.current];
      headerRow.forEach((part, index) => {
        if (index < MAX_STORED_PAGE_COUNT) {
          nextLangs[index] = detectLangFromHeader(part);
        }
      });
      dispatch({ type: 'SET_PAGE_LANGS', value: nextLangs });
    },
    [dispatch, pageNamesRef, pageLangsRef, lastAppliedHeaderKeyRef],
  );

  const syncDraftFromText = useCallback(
    (text: string, currentSepKey: string, currentPageCount: number) => {
      const syncKey = `${text.length}|${currentSepKey}|${currentPageCount}|${firstRowIsHeaderRef.current ? '1' : '0'}`;
      if (syncKey === lastSyncedKeyRef.current) return;
      lastSyncedKeyRef.current = syncKey;

      if (!text.trim()) {
        lastAppliedHeaderKeyRef.current = null;
        dispatch({ type: 'SET_CARDS', value: [] });
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
        dispatch({ type: 'SET_FIRST_ROW_IS_HEADER', value: effectiveFirstRowIsHeader });
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
    [
      headerSettingTouchedRef,
      firstRowIsHeaderRef,
      lastSyncedKeyRef,
      preHeaderPageNamesRef,
      pageNamesRef,
      lastAppliedHeaderKeyRef,
      rebuildPreviewFromText,
      dispatch,
      syncHeaderConfigFromText,
    ],
  );

  const applyDetectedText = useCallback(
    (text: string, nextName?: string) => {
      const { safeText, wasLimited } = limitImportLines(text, IMPORT_LINE_LIMIT);

      if (nextName) {
        dispatch({ type: 'SET_NAME', value: nextName });
      }

      preHeaderPageNamesRef.current = null;
      headerSettingTouchedRef.current = false;
      lastAppliedHeaderKeyRef.current = null;
      dispatch({
        type: 'SET_IMPORT_ERROR',
        value: wasLimited ? 'import.err.too_many_lines' : '',
      });
      dispatch({ type: 'SET_RAW_TEXT', value: safeText });

      if (!safeText.trim()) {
        dispatch({ type: 'SET_RAW_COLUMN_COUNT', value: MIN_PAGE_COUNT });
        dispatch({ type: 'SET_CARDS', value: [] });
        return;
      }

      dispatch({
        type: 'SET_PAGE_NAMES',
        value: Array.from({ length: MAX_STORED_PAGE_COUNT }, () => ''),
      });
      dispatch({
        type: 'SET_PAGE_LANGS',
        value: Array.from({ length: MAX_STORED_PAGE_COUNT }, () => ''),
      });

      const { detectedSep, detectedCount } = detectTextProperties(safeText);

      if (detectedCount > MAX_STORED_PAGE_COUNT) {
        dispatch({ type: 'SET_IMPORT_ERROR', value: 'import.err.too_many_columns' });
        return;
      }

      dispatch({ type: 'SET_RAW_COLUMN_COUNT', value: detectedCount });

      const effectiveSep = sepTouchedRef.current ? sepKey : detectedSep;

      if (!sepTouchedRef.current) {
        dispatch({ type: 'SET_SEP_KEY', value: detectedSep });
        dispatch({ type: 'SET_CUSTOM_SEP', value: '' });
      }

      const nextPageCount = Math.max(MIN_PAGE_COUNT, detectedCount);
      dispatch({ type: 'SET_PAGE_COUNT', value: nextPageCount });
      syncDraftFromText(safeText, effectiveSep, nextPageCount);
    },
    [
      dispatch,
      headerSettingTouchedRef,
      lastAppliedHeaderKeyRef,
      preHeaderPageNamesRef,
      sepKey,
      sepTouchedRef,
      syncDraftFromText,
    ],
  );

  useEffect(() => {
    if (debouncedRawText.trim()) {
      const activeSep = getActiveSepValue(sepKey, customSep);
      const detectedCount = detectPageCount(
        debouncedRawText,
        activeSep,
        firstRowIsHeaderRef.current,
      );
      dispatch({ type: 'SET_RAW_COLUMN_COUNT', value: detectedCount });
    }
    syncDraftFromText(debouncedRawText, getActiveSepValue(sepKey, customSep), pageCount);
  }, [
    customSep,
    debouncedRawText,
    firstRowIsHeaderRef,
    pageCount,
    sepKey,
    dispatch,
    syncDraftFromText,
  ]);

  return { syncDraftFromText, applyDetectedText, syncHeaderConfigFromText };
}
