import type { Flashcard, FlashcardGroup, StudyMode } from '../../types/models';
import { normalizeStudyFilter } from '../storeDataNormalization';
import type { StoreData } from './localPersistence';

export const FIRESTORE_SCHEMA_VERSION = 2;

export type UserData = StoreData;

export interface FirestoreDeckDoc {
  id: string;
  name: string;
  activeModeId: string;
  pageLanguages: string[];
  pageNames: string[];
  activePageCount?: number;
  studyFilter?: FlashcardGroup['studyFilter'];
  updatedAt?: number;
  deletedAt?: number | null;
  archivedAt?: number | null;
}

export interface FirestoreCardDoc extends Flashcard {
  updatedAt?: unknown;
}

export interface FirestoreStudyModeDoc {
  id: string;
  name: string;
  steps: StudyMode['steps'];
  isBuiltIn: boolean;
  builtInSourceId?: string;
  updatedAt?: unknown;
  deletedAt?: number | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function requireString(value: unknown, label: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`Firestore document is missing a valid ${label}.`);
  }

  return value;
}

function requireFiniteNumber(value: unknown, label: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`Firestore document is missing a valid ${label}.`);
  }

  return value;
}

function deserializeSrsState(docId: string, value: unknown): Flashcard['srsState'] {
  if (!isRecord(value)) {
    throw new Error(`Firestore card ${docId} is missing a valid srsState.`);
  }

  return {
    difficulty: requireFiniteNumber(value.difficulty, `srsState.difficulty for card ${docId}`),
    stability: requireFiniteNumber(value.stability, `srsState.stability for card ${docId}`),
    repetitions: requireFiniteNumber(value.repetitions, `srsState.repetitions for card ${docId}`),
    state: requireFiniteNumber(value.state, `srsState.state for card ${docId}`),
    lastReviewTimestamp: requireFiniteNumber(
      value.lastReviewTimestamp,
      `srsState.lastReviewTimestamp for card ${docId}`,
    ),
    nextReviewTimestamp: requireFiniteNumber(
      value.nextReviewTimestamp,
      `srsState.nextReviewTimestamp for card ${docId}`,
    ),
  };
}

export function serializeDeckDoc(group: FlashcardGroup): FirestoreDeckDoc {
  return {
    id: group.id,
    name: group.name,
    activeModeId: group.activeModeId,
    pageLanguages: group.pageLanguages,
    pageNames: group.pageNames,
    activePageCount: group.activePageCount,
    studyFilter: normalizeStudyFilter(group.studyFilter),
    updatedAt: group.updatedAt,
    ...(group.deletedAt != null ? { deletedAt: group.deletedAt } : {}),
    archivedAt: group.archivedAt ?? null,
  };
}

export function serializeCardDoc(card: Flashcard): FirestoreCardDoc {
  return {
    id: card.id,
    pages: card.pages,
    srsState: card.srsState,
    contentUpdatedAt: card.contentUpdatedAt,
    srsUpdatedAt: card.srsUpdatedAt,
    ...(card.deletedAt != null ? { deletedAt: card.deletedAt } : {}),
  };
}

export function serializeStudyModeDoc(mode: StudyMode): FirestoreStudyModeDoc {
  return {
    id: mode.id,
    name: mode.name,
    steps: mode.steps,
    isBuiltIn: mode.isBuiltIn,
    ...(mode.builtInSourceId ? { builtInSourceId: mode.builtInSourceId } : {}),
    updatedAt: mode.updatedAt,
    ...(mode.deletedAt != null ? { deletedAt: mode.deletedAt } : {}),
  };
}

export function cloneUserData<T extends UserData>(data: T): T {
  return JSON.parse(JSON.stringify(data)) as T;
}

export function deserializeCardDoc(docId: string, rawData: unknown): Flashcard {
  if (!isRecord(rawData)) {
    throw new Error(`Firestore card ${docId} is malformed.`);
  }
  if (!Array.isArray(rawData.pages)) {
    throw new Error(`Firestore card ${docId} is missing pages or srsState.`);
  }

  return {
    id: typeof rawData.id === 'string' && rawData.id.trim().length > 0 ? rawData.id : docId,
    pages: rawData.pages.filter((page): page is string => typeof page === 'string'),
    srsState: deserializeSrsState(docId, rawData.srsState),
    contentUpdatedAt:
      typeof rawData.contentUpdatedAt === 'number' ? rawData.contentUpdatedAt : undefined,
    srsUpdatedAt: typeof rawData.srsUpdatedAt === 'number' ? rawData.srsUpdatedAt : undefined,
    deletedAt:
      rawData.deletedAt != null && typeof rawData.deletedAt === 'number'
        ? rawData.deletedAt
        : undefined,
  };
}

export function deserializeDeckDoc(
  docId: string,
  rawData: unknown,
  cards: Flashcard[],
): FlashcardGroup {
  if (!isRecord(rawData)) {
    throw new Error(`Firestore deck ${docId} is malformed.`);
  }

  const pageNames = asStringArray(rawData.pageNames);
  const pageLanguages = asStringArray(rawData.pageLanguages);
  const fallbackPageCount = Math.max(pageNames.length, pageLanguages.length, 2);
  const rawActivePageCount = rawData.activePageCount;
  const activePageCount =
    typeof rawActivePageCount === 'number' && Number.isFinite(rawActivePageCount)
      ? rawActivePageCount
      : fallbackPageCount;

  return {
    id: typeof rawData.id === 'string' && rawData.id.trim().length > 0 ? rawData.id : docId,
    name: requireString(rawData.name, 'name'),
    cards,
    activeModeId: typeof rawData.activeModeId === 'string' ? rawData.activeModeId : '',
    pageLanguages,
    pageNames,
    activePageCount,
    studyFilter: normalizeStudyFilter(rawData.studyFilter),
    updatedAt: typeof rawData.updatedAt === 'number' ? rawData.updatedAt : undefined,
    deletedAt:
      rawData.deletedAt != null && typeof rawData.deletedAt === 'number'
        ? rawData.deletedAt
        : undefined,
    archivedAt:
      typeof rawData.archivedAt === 'number' ? rawData.archivedAt : undefined,
  };
}

export function deserializeStudyModeDoc(docId: string, rawData: unknown): StudyMode {
  if (!isRecord(rawData)) {
    throw new Error(`Firestore study mode ${docId} is malformed.`);
  }
  if (!Array.isArray(rawData.steps)) {
    throw new Error(`Firestore study mode ${docId} is missing steps.`);
  }

  return {
    id: typeof rawData.id === 'string' && rawData.id.trim().length > 0 ? rawData.id : docId,
    name: requireString(rawData.name, 'study mode name'),
    steps: rawData.steps as StudyMode['steps'],
    isBuiltIn: rawData.isBuiltIn === true,
    ...(typeof rawData.builtInSourceId === 'string' ? { builtInSourceId: rawData.builtInSourceId } : {}),
    updatedAt: typeof rawData.updatedAt === 'number' ? rawData.updatedAt : undefined,
    deletedAt:
      rawData.deletedAt != null && typeof rawData.deletedAt === 'number'
        ? rawData.deletedAt
        : undefined,
  };
}

export function deserializeActivityDayCount(docId: string, rawData: unknown): number {
  if (!isRecord(rawData) || typeof rawData.count !== 'number' || !Number.isFinite(rawData.count)) {
    throw new Error(`Firestore activity day ${docId} is missing a valid count.`);
  }

  return rawData.count;
}
