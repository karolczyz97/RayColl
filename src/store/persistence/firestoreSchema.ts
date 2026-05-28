import type { Flashcard, FlashcardGroup, StudyMode } from '../../types/models';
import { normalizeStudyFilter } from '../storeDataNormalization';
import type { StoreData } from './localPersistence';

export const FIRESTORE_SCHEMA_VERSION = 2;

export type UserData = StoreData;

export interface FirestoreUserRootDoc {
  schemaVersion: number;
  updatedAt?: unknown;
  legacyBackup?: UserData;
}

export interface FirestoreDeckDoc {
  id: string;
  name: string;
  activeModeId: string;
  pageLanguages: string[];
  pageNames: string[];
  activePageCount?: number;
  studyFilter?: FlashcardGroup['studyFilter'];
  updatedAt?: unknown;
}

export interface FirestoreCardDoc extends Flashcard {
  updatedAt?: unknown;
}

export interface FirestoreStudyModeDoc extends StudyMode {
  updatedAt?: unknown;
}

export interface FirestoreActivityDayDoc {
  count: number;
  updatedAt?: unknown;
}

export function serializeDeckDoc(group: FlashcardGroup): Omit<FirestoreDeckDoc, 'updatedAt'> {
  return {
    id: group.id,
    name: group.name,
    activeModeId: group.activeModeId,
    pageLanguages: group.pageLanguages,
    pageNames: group.pageNames,
    activePageCount: group.activePageCount,
    studyFilter: normalizeStudyFilter(group.studyFilter),
  };
}

export function serializeCardDoc(card: Flashcard): Omit<FirestoreCardDoc, 'updatedAt'> {
  return {
    id: card.id,
    pages: card.pages,
    srsState: card.srsState,
  };
}

export function serializeStudyModeDoc(mode: StudyMode): Omit<FirestoreStudyModeDoc, 'updatedAt'> {
  return {
    id: mode.id,
    name: mode.name,
    steps: mode.steps,
  };
}

export function cloneUserData<T extends UserData>(data: T): T {
  return JSON.parse(JSON.stringify(data)) as T;
}
