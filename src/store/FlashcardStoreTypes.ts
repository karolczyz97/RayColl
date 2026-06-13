import type { User } from 'firebase/auth';
import type { Flashcard, FlashcardGroup, StudyMode } from '@/types/models';
import type { CardFilter } from '@/constants/cardFilters';
import type { CardOrder } from '@/constants/cardOrder';
import type { ImportDeckPayload, ImportDeckResult } from '@/import/importDeck';

export type SyncStatus = 'idle' | 'loading' | 'saving' | 'syncing' | 'error';
export type CloudMode = 'debounced' | 'study' | 'none';

export interface PersistOptions {
  immediate?: boolean;
  cloudMode?: CloudMode;
}

export interface FlashcardStore {
  groups: FlashcardGroup[];
  archivedGroups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  isLoading: boolean;
  user: User | null;
  syncStatus: SyncStatus;
  lastSyncError: string | null;
  lastPersistenceError: string | null;
  lastStoreError: string | null;
  lastLoginError: string | null;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  clearLastLoginError: () => void;
  flushPersistence: () => Promise<void>;
  addGroup: (name: string, languages: string[], pageNames: string[]) => string;
  addGroupWithCards: (
    name: string,
    languages: string[],
    pageNames: string[],
    cards: Omit<Flashcard, 'id' | 'srsState'>[],
  ) => string;
  importDeck: (payload: ImportDeckPayload) => Promise<ImportDeckResult>;
  updateGroup: (group: FlashcardGroup) => void;
  deleteGroup: (groupId: string) => Promise<void>;
  archiveGroup: (groupId: string) => Promise<void>;
  restoreGroup: (groupId: string) => Promise<void>;
  purgeArchives: () => void;
  addFlashcard: (groupId: string, pages: string[]) => string;
  updateFlashcard: (groupId: string, card: Flashcard) => void;
  deleteFlashcard: (groupId: string, cardId: string) => void;
  reviewFlashcard: (groupId: string, cardId: string, rating: number) => void;
  addStudyMode: (mode: StudyMode) => void;
  updateStudyMode: (mode: StudyMode) => void;
  deleteStudyMode: (modeId: string) => void;
  resetStudyMode: (modeId: string) => void;
  resetToDefault: () => Promise<void>;
  recordActivity: () => void;
  getDueCards: (groupId: string) => Flashcard[];
  getGroupProgress: (groupId: string) => number;
  importState: (json: string) => Promise<void>;
  exportState: () => string;
  setVisiblePageCount: (groupId: string, count: number) => void;
  setStudyFilter: (groupId: string, filter: CardFilter) => void;
  setCardOrder: (groupId: string, order: CardOrder) => void;
  setActiveStudyMode: (groupId: string, modeId: string) => void;
  addFlashcardsBulk: (groupId: string, cards: Flashcard[]) => void;
}

export type FlashcardStoreState = Pick<
  FlashcardStore,
  | 'groups'
  | 'archivedGroups'
  | 'studyModes'
  | 'activityHeatmap'
  | 'isLoading'
  | 'user'
  | 'syncStatus'
  | 'lastSyncError'
  | 'lastPersistenceError'
  | 'lastStoreError'
  | 'lastLoginError'
>;

export type FlashcardStoreActions = Omit<FlashcardStore, keyof FlashcardStoreState>;
