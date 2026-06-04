import { useCallback, useMemo } from 'react';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import type { Flashcard, FlashcardGroup, StudyMode, StoreData } from '@/types/models';
import type { CardFilter } from '@/constants/cardFilters';
import type { ImportDeckPayload, ImportDeckResult } from '@/import/importDeck';
import { validateImportDeckPayload } from '@/import/importDeck';
import { validateBackupData } from '@/utils/backupValidation';
import {
  addGroupAction,
  addGroupWithCardsAction,
  archiveGroupAction,
  deleteGroupAction,
  purgeExpiredArchivesAction,
  restoreGroupAction,
  setActiveStudyModeAction,
  setStudyFilterAction,
  setVisiblePageCountAction,
  updateGroupAction,
} from './actions/groupActions';
import {
  addFlashcardAction,
  addFlashcardsBulkAction,
  deleteFlashcardAction,
  reviewCardAction,
  updateFlashcardAction,
} from './actions/cardActions';
import {
  addStudyModeAction,
  deleteStudyModeAction,
  updateStudyModeAction,
} from './actions/studyModeActions';
import { ARCHIVE_RETENTION_MS } from '@/constants/archive';
import { recordActivityAction } from './actions/activityActions';
import { createSeedGroups, SEED_VERSION } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { getDueCards as selectDueCards } from './selectors/dueCards';
import { getGroupProgress as selectGroupProgress } from './selectors/progress';
import { selectLiveStudyModes } from './selectors/tombstones';
import { filterLive } from '@/utils/array';
import { setSeedVersion } from './persistence/localPersistence';
import type { PersistOptions } from './FlashcardStoreTypes';
import { normalizeStoreData, CURRENT_SCHEMA_VERSION } from './storeDataNormalization';
import { getErrorMessage } from '@/utils/errors';
import { captureSnapshot, persistWithRollback } from './rollbackHelper';
import { parseBackupJson } from './backupImport';

interface UseStoreActionsParams {
  groupsRef: MutableRefObject<FlashcardGroup[]>;
  studyModesRef: MutableRefObject<StudyMode[]>;
  heatmapRef: MutableRefObject<Record<string, number>>;
  setGroups: Dispatch<SetStateAction<FlashcardGroup[]>>;
  setHeatmap: Dispatch<SetStateAction<Record<string, number>>>;
  setSyncStatus: Dispatch<SetStateAction<'idle' | 'loading' | 'saving' | 'syncing' | 'error'>>;
  setLastPersistenceError: Dispatch<SetStateAction<string | null>>;
  setLastStoreError: Dispatch<SetStateAction<string | null>>;
  getCurrentUid: () => string | null;
  applySnapshot: (snapshot: StoreData) => void;
  persistNow: (snapshot: StoreData & { uid: string | null }) => Promise<void>;
  persistCurrentSnapshot: (options?: PersistOptions) => void;
  flushPersistence: () => Promise<void>;
  commitGroups: (nextGroups: FlashcardGroup[], options?: PersistOptions) => void;
  commitStudyModes: (nextStudyModes: StudyMode[], options?: PersistOptions) => void;
  commitHeatmap: (nextHeatmap: Record<string, number>, options?: PersistOptions) => void;
}

export function useStoreActionsCore({
  groupsRef,
  studyModesRef,
  heatmapRef,
  setGroups,
  setHeatmap,
  setSyncStatus,
  setLastPersistenceError,
  setLastStoreError,
  getCurrentUid,
  applySnapshot,
  persistNow,
  persistCurrentSnapshot,
  flushPersistence,
  commitGroups,
  commitStudyModes,
  commitHeatmap,
}: UseStoreActionsParams) {
  const addGroup = useCallback(
    (name: string, languages: string[], pageNames: string[]) => {
      const { nextGroups, newGroupId } = addGroupAction(
        groupsRef.current,
        name,
        languages,
        pageNames,
      );
      commitGroups(nextGroups);
      return newGroupId;
    },
    [commitGroups, groupsRef],
  );

  const addGroupWithCards = useCallback(
    (
      name: string,
      languages: string[],
      pageNames: string[],
      cards: Omit<Flashcard, 'id' | 'srsState'>[],
    ) => {
      const { nextGroups, newGroupId } = addGroupWithCardsAction(
        groupsRef.current,
        name,
        languages,
        pageNames,
        cards,
      );
      commitGroups(nextGroups);
      return newGroupId;
    },
    [commitGroups, groupsRef],
  );

  const importDeck = useCallback(
    async (payload: ImportDeckPayload): Promise<ImportDeckResult> => {
      let normalized;
      try {
        normalized = validateImportDeckPayload(payload);
      } catch (err) {
        return { ok: false, error: getErrorMessage(err) };
      }

      const previousSnapshot: StoreData = captureSnapshot(groupsRef, studyModesRef, heatmapRef);
      const currentUid = getCurrentUid();
      const { nextGroups, newGroupId } = addGroupWithCardsAction(
        previousSnapshot.groups,
        normalized.name,
        normalized.languages,
        normalized.pageNames,
        normalized.cards,
      );

      try {
        await flushPersistence();
      } catch (err) {
        const message = getErrorMessage(err);
        setLastPersistenceError(message);
        setLastStoreError(message);
        setSyncStatus('error');
        return { ok: false, error: message };
      }

      groupsRef.current = nextGroups;
      setGroups(nextGroups);

      try {
        await persistWithRollback(
          applySnapshot,
          persistNow,
          previousSnapshot,
          { groups: nextGroups, studyModes: previousSnapshot.studyModes, activityHeatmap: previousSnapshot.activityHeatmap },
          currentUid,
          'Import deck',
        );
        return {
          ok: true,
          groupId: newGroupId,
          importedCards: normalized.cards.length,
        };
      } catch (err) {
        const message = getErrorMessage(err);
        setLastPersistenceError(message);
        setLastStoreError(message);
        setSyncStatus('error');
        return { ok: false, error: message };
      }
    },
    [
      applySnapshot,
      flushPersistence,
      getCurrentUid,
      groupsRef,
      heatmapRef,
      persistNow,
      setGroups,
      setLastPersistenceError,
      setLastStoreError,
      setSyncStatus,
      studyModesRef,
    ],
  );

  const updateGroup = useCallback(
    (group: FlashcardGroup) => {
      commitGroups(updateGroupAction(groupsRef.current, group));
    },
    [commitGroups, groupsRef],
  );

  // Best-effort: these are invoked fire-and-forget from screens. A failed flush must not
  // produce an unhandled rejection — the error is already surfaced via syncStatus /
  // lastPersistenceError / lastSyncError (shown by SyncStatusBanner).
  const commitGroupsAndFlush = useCallback(
    async (next: FlashcardGroup[]) => {
      groupsRef.current = next;
      setGroups(next);
      try {
        await flushPersistence();
      } catch (err) {
        console.error('Critical group op persistence failed:', err);
      }
    },
    [flushPersistence, groupsRef, setGroups],
  );

  const deleteGroup = useCallback(
    (groupId: string) => commitGroupsAndFlush(deleteGroupAction(groupsRef.current, groupId)),
    [commitGroupsAndFlush, groupsRef],
  );

  const archiveGroup = useCallback(
    (groupId: string) => commitGroupsAndFlush(archiveGroupAction(groupsRef.current, groupId)),
    [commitGroupsAndFlush, groupsRef],
  );

  const restoreGroup = useCallback(
    (groupId: string) => commitGroupsAndFlush(restoreGroupAction(groupsRef.current, groupId)),
    [commitGroupsAndFlush, groupsRef],
  );

  const purgeArchives = useCallback(() => {
    const { groups, changed } = purgeExpiredArchivesAction(
      groupsRef.current,
      Date.now(),
      ARCHIVE_RETENTION_MS,
    );
    if (changed) commitGroups(groups, { immediate: true });
  }, [commitGroups, groupsRef]);

  const setVisiblePageCount = useCallback(
    (groupId: string, count: number) => {
      commitGroups(setVisiblePageCountAction(groupsRef.current, groupId, count));
    },
    [commitGroups, groupsRef],
  );

  const setStudyFilter = useCallback(
    (groupId: string, filter: CardFilter) => {
      commitGroups(setStudyFilterAction(groupsRef.current, groupId, filter));
    },
    [commitGroups, groupsRef],
  );

  const setActiveStudyMode = useCallback(
    (groupId: string, modeId: string) => {
      commitGroups(setActiveStudyModeAction(groupsRef.current, groupId, modeId));
    },
    [commitGroups, groupsRef],
  );

  const addFlashcard = useCallback(
    (groupId: string, pages: string[]) => {
      const { nextGroups, newCardId } = addFlashcardAction(groupsRef.current, groupId, pages);
      commitGroups(nextGroups);
      return newCardId;
    },
    [commitGroups, groupsRef],
  );

  const updateFlashcard = useCallback(
    (groupId: string, card: Flashcard) => {
      commitGroups(updateFlashcardAction(groupsRef.current, groupId, card));
    },
    [commitGroups, groupsRef],
  );

  const deleteFlashcard = useCallback(
    (groupId: string, cardId: string) => {
      commitGroups(deleteFlashcardAction(groupsRef.current, groupId, cardId));
    },
    [commitGroups, groupsRef],
  );

  const reviewFlashcard = useCallback(
    (groupId: string, card: Flashcard) => {
      const { nextGroups, nextHeatmap } = reviewCardAction(
        groupsRef.current,
        groupId,
        card,
        heatmapRef.current,
      );

      groupsRef.current = nextGroups;
      heatmapRef.current = nextHeatmap;

      setGroups(nextGroups);
      setHeatmap(nextHeatmap);

      persistCurrentSnapshot({ cloudMode: 'study' });
    },
    [groupsRef, heatmapRef, persistCurrentSnapshot, setGroups, setHeatmap],
  );

  const addFlashcardsBulk = useCallback(
    (groupId: string, cards: Flashcard[]) => {
      commitGroups(addFlashcardsBulkAction(groupsRef.current, groupId, cards));
    },
    [commitGroups, groupsRef],
  );

  const addStudyMode = useCallback(
    (mode: StudyMode) => {
      commitStudyModes(addStudyModeAction(studyModesRef.current, mode));
    },
    [commitStudyModes, studyModesRef],
  );

  const updateStudyMode = useCallback(
    (mode: StudyMode) => {
      commitStudyModes(updateStudyModeAction(studyModesRef.current, mode));
    },
    [commitStudyModes, studyModesRef],
  );

  const deleteStudyMode = useCallback(
    (modeId: string) => {
      commitStudyModes(deleteStudyModeAction(studyModesRef.current, modeId));
    },
    [commitStudyModes, studyModesRef],
  );

  const resetStudyMode = useCallback(
    (modeId: string) => {
      const currentMode = studyModesRef.current.find((mode) => mode.id === modeId);
      const sourceId = currentMode?.builtInSourceId;
      if (!currentMode?.isBuiltIn || !sourceId) return;

      const seed = createSeedModes().find((mode) => mode.id === sourceId);
      if (!seed) return;

      commitStudyModes(updateStudyModeAction(studyModesRef.current, {
        ...seed,
        id: currentMode.id,
        isBuiltIn: true,
        builtInSourceId: sourceId,
      }));
    },
    [commitStudyModes, studyModesRef],
  );

  const resetToDefault = useCallback(async () => {
    const previousSnapshot: StoreData = captureSnapshot(groupsRef, studyModesRef, heatmapRef);
    const seedSnapshot: StoreData = {
      groups: createSeedGroups(),
      studyModes: createSeedModes(),
      activityHeatmap: {},
    };

    applySnapshot(seedSnapshot);

    await persistWithRollback(
      applySnapshot,
      persistNow,
      previousSnapshot,
      seedSnapshot,
      getCurrentUid(),
      'Reset to default',
    );

    setSeedVersion(SEED_VERSION).catch((err) => {
      setLastPersistenceError(getErrorMessage(err));
    });
  }, [
    applySnapshot,
    getCurrentUid,
    groupsRef,
    heatmapRef,
    persistNow,
    setLastPersistenceError,
    studyModesRef,
  ]);

  const recordActivity = useCallback(() => {
    const { nextHeatmap } = recordActivityAction(heatmapRef.current);
    commitHeatmap(nextHeatmap);
  }, [commitHeatmap, heatmapRef]);

  const getDueCards = useCallback(
    (groupId: string): Flashcard[] => {
      const group = groupsRef.current.find((item) => item.id === groupId);
      if (!group) return [];
      return selectDueCards(group.cards, group.studyFilter);
    },
    [groupsRef],
  );

  const getGroupProgress = useCallback(
    (groupId: string): number => {
      const group = groupsRef.current.find((item) => item.id === groupId);
      if (!group) return 0;
      return selectGroupProgress(group.cards);
    },
    [groupsRef],
  );

  const exportState = useCallback(() => {
    const exportGroups = filterLive(groupsRef.current).map((g) => ({
      ...g,
      cards: filterLive(g.cards),
    }));
    return JSON.stringify(
      {
        groups: exportGroups,
        studyModes: selectLiveStudyModes(studyModesRef.current),
        activityHeatmap: heatmapRef.current,
        schemaVersion: CURRENT_SCHEMA_VERSION,
      },
      null,
      2,
    );
  }, [groupsRef, heatmapRef, studyModesRef]);

  const importState = useCallback(
    async (json: string) => {
      const previousSnapshot: StoreData = captureSnapshot(groupsRef, studyModesRef, heatmapRef);

      const data = parseBackupJson(json);
      if (!validateBackupData(data)) {
        throw new Error('app_settings.import_error');
      }

      const normalized = normalizeStoreData({
        groups: data.groups,
        studyModes: data.studyModes,
        activityHeatmap: data.activityHeatmap,
        schemaVersion: data.schemaVersion,
      });

      applySnapshot(normalized);

      await persistWithRollback(
        applySnapshot,
        persistNow,
        previousSnapshot,
        normalized,
        getCurrentUid(),
        'Import state',
      );
    },
    [applySnapshot, getCurrentUid, groupsRef, heatmapRef, persistNow, studyModesRef],
  );

  return useMemo(
    () => ({
      addGroup,
      addGroupWithCards,
      importDeck,
      updateGroup,
      deleteGroup,
      archiveGroup,
      restoreGroup,
      purgeArchives,
      addFlashcard,
      updateFlashcard,
      deleteFlashcard,
      reviewFlashcard,
      addFlashcardsBulk,
      addStudyMode,
      updateStudyMode,
      deleteStudyMode,
      resetStudyMode,
      resetToDefault,
      recordActivity,
      getDueCards,
      getGroupProgress,
      exportState,
      importState,
      setVisiblePageCount,
      setStudyFilter,
      setActiveStudyMode,
    }),
    [
      addGroup,
      addGroupWithCards,
      importDeck,
      updateGroup,
      deleteGroup,
      archiveGroup,
      restoreGroup,
      purgeArchives,
      addFlashcard,
      updateFlashcard,
      deleteFlashcard,
      reviewFlashcard,
      addFlashcardsBulk,
      addStudyMode,
      updateStudyMode,
      deleteStudyMode,
      resetStudyMode,
      resetToDefault,
      recordActivity,
      getDueCards,
      getGroupProgress,
      exportState,
      importState,
      setVisiblePageCount,
      setStudyFilter,
      setActiveStudyMode,
    ],
  );
}
