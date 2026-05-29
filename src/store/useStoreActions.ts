import { useCallback } from 'react';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import type { Flashcard, FlashcardGroup, StudyMode } from '../types/models';
import type { CardFilter } from '../constants/cardFilters';
import type { ImportDeckPayload, ImportDeckResult } from '../import/importDeck';
import { validateImportDeckPayload } from '../import/importDeck';
import { validateBackupData } from '../utils/backupValidation';
import {
  addGroupAction,
  addGroupWithCardsAction,
  deleteGroupAction,
  setActiveStudyModeAction,
  setStudyFilterAction,
  setVisiblePageCountAction,
  updateGroupAction,
} from './actions/groupActions';
import {
  addFlashcardAction,
  addFlashcardsBulkAction,
  deleteFlashcardAction,
  updateFlashcardAction,
} from './actions/cardActions';
import {
  addStudyModeAction,
  deleteStudyModeAction,
  updateStudyModeAction,
} from './actions/studyModeActions';
import { recordActivityAction } from './actions/activityActions';
import { createSeedGroups, SEED_VERSION } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { getDueCards as selectDueCards } from './selectors/dueCards';
import { getGroupProgress as selectGroupProgress } from './selectors/progress';
import { setSeedVersion, type StoreData } from './persistence/localPersistence';
import type { PersistOptions } from './FlashcardStoreTypes';
import { normalizeStoreData } from './storeDataNormalization';

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

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

      const previousSnapshot: StoreData = {
        groups: groupsRef.current,
        studyModes: studyModesRef.current,
        activityHeatmap: heatmapRef.current,
      };
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
        await persistNow({
          uid: currentUid,
          groups: nextGroups,
          studyModes: previousSnapshot.studyModes,
          activityHeatmap: previousSnapshot.activityHeatmap,
        });
        return {
          ok: true,
          groupId: newGroupId,
          importedCards: normalized.cards.length,
        };
      } catch (err) {
        applySnapshot(previousSnapshot);
        try {
          await persistNow({ uid: currentUid, ...previousSnapshot });
        } catch (rollbackErr) {
          console.error('Import rollback persistence failed:', rollbackErr);
        }
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

  const deleteGroup = useCallback(
    (groupId: string) => {
      commitGroups(deleteGroupAction(groupsRef.current, groupId), { immediate: true });
    },
    [commitGroups, groupsRef],
  );

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
      const nextGroups = updateFlashcardAction(groupsRef.current, groupId, card);
      const { nextHeatmap } = recordActivityAction(heatmapRef.current);

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

  const resetToDefault = useCallback(() => {
    const groupsSnapshot = createSeedGroups();
    const modesSnapshot = createSeedModes();
    const heatmapSnapshot: Record<string, number> = {};

    applySnapshot({
      groups: groupsSnapshot,
      studyModes: modesSnapshot,
      activityHeatmap: heatmapSnapshot,
    });

    setSeedVersion(SEED_VERSION).catch((err) => {
      setLastPersistenceError(getErrorMessage(err));
    });

    persistCurrentSnapshot({ immediate: true });
  }, [applySnapshot, persistCurrentSnapshot, setLastPersistenceError]);

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
    return JSON.stringify(
      {
        groups: groupsRef.current,
        studyModes: studyModesRef.current,
        activityHeatmap: heatmapRef.current,
      },
      null,
      2,
    );
  }, [groupsRef, heatmapRef, studyModesRef]);

  const importState = useCallback(
    (json: string) => {
      const data = JSON.parse(json);
      validateBackupData(data);

      applySnapshot(normalizeStoreData({
        groups: data.groups,
        studyModes: data.studyModes,
        activityHeatmap: data.activityHeatmap,
      }));

      persistCurrentSnapshot({ immediate: true });
    },
    [applySnapshot, persistCurrentSnapshot],
  );

  return {
    addGroup,
    addGroupWithCards,
    importDeck,
    updateGroup,
    deleteGroup,
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
  };
}
