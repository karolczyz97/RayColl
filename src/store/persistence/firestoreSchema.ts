import type { Flashcard, FlashcardGroup, SrsState, StudyMode, StoreData } from '@/types/models';
import { normalizeStudyFilter } from '@/store/storeDataNormalization';
import { normalizeCardOrder } from '@/constants/cardOrder';
import { assertStudyModeStep } from '@/utils/backupValidation';
import { isRecord } from '@/utils/types';

export type UserData = StoreData;

export interface FirestoreDeckDoc {
  id: string;
  name: string;
  activeModeId: string;
  pageLanguages: string[];
  pageNames: string[];
  activePageCount: number;
  studyFilter: FlashcardGroup['studyFilter'];
  cardOrder: FlashcardGroup['cardOrder'];
  updatedAt: number;
  deletedAt?: number | null;
  archivedAt?: number | null;
}

export interface FirestoreCardDoc {
  id: string;
  pages: string[];
  srsState: SrsState;
  contentUpdatedAt: number;
  srsUpdatedAt: number;
  deletedAt?: number | null;
}

export interface FirestoreStudyModeDoc {
  id: string;
  name: string;
  steps: StudyMode['steps'];
  isBuiltIn: boolean;
  builtInSourceId?: string;
  updatedAt: number;
  deletedAt?: number | null;
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

function requireStringArray(value: unknown, label: string): string[] {
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) {
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
    state: requireFiniteNumber(
      value.state,
      `srsState.state for card ${docId}`,
    ) as SrsState['state'],
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
    cardOrder: normalizeCardOrder(group.cardOrder),
    updatedAt: group.updatedAt,
    ...(group.deletedAt != null ? { deletedAt: group.deletedAt } : {}),
    ...(group.archivedAt != null ? { archivedAt: group.archivedAt } : {}),
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
  return structuredClone(data);
}

export function deserializeCardDoc(docId: string, rawData: unknown): Flashcard {
  if (!isRecord(rawData)) {
    throw new Error(`Firestore card ${docId} is malformed.`);
  }
  if (!Array.isArray(rawData.pages)) {
    throw new Error(`Firestore card ${docId} is missing pages or srsState.`);
  }
  if (!rawData.pages.every((page) => typeof page === 'string')) {
    throw new Error(`Firestore card ${docId} has invalid pages.`);
  }

  return {
    id: typeof rawData.id === 'string' && rawData.id.trim().length > 0 ? rawData.id : docId,
    pages: rawData.pages,
    srsState: deserializeSrsState(docId, rawData.srsState),
    contentUpdatedAt: requireFiniteNumber(rawData.contentUpdatedAt, `contentUpdatedAt for card ${docId}`),
    srsUpdatedAt: requireFiniteNumber(rawData.srsUpdatedAt, `srsUpdatedAt for card ${docId}`),
    ...(rawData.deletedAt != null && typeof rawData.deletedAt === 'number'
      ? { deletedAt: rawData.deletedAt }
      : {}),
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

  const pageNames = requireStringArray(rawData.pageNames, `pageNames for deck ${docId}`);
  const pageLanguages = requireStringArray(rawData.pageLanguages, `pageLanguages for deck ${docId}`);
  const studyFilter = normalizeStudyFilter(rawData.studyFilter);
  if (studyFilter !== rawData.studyFilter) {
    throw new Error(`Firestore deck ${docId} has invalid studyFilter.`);
  }
  const cardOrder = normalizeCardOrder(rawData.cardOrder);
  if (cardOrder !== rawData.cardOrder) {
    throw new Error(`Firestore deck ${docId} has invalid cardOrder.`);
  }

  return {
    id: typeof rawData.id === 'string' && rawData.id.trim().length > 0 ? rawData.id : docId,
    name: requireString(rawData.name, 'name'),
    cards,
    activeModeId: requireString(rawData.activeModeId, `activeModeId for deck ${docId}`),
    pageLanguages,
    pageNames,
    activePageCount: requireFiniteNumber(rawData.activePageCount, `activePageCount for deck ${docId}`),
    studyFilter,
    cardOrder,
    updatedAt: requireFiniteNumber(rawData.updatedAt, `updatedAt for deck ${docId}`),
    ...(rawData.deletedAt != null && typeof rawData.deletedAt === 'number'
      ? { deletedAt: rawData.deletedAt }
      : {}),
    ...(typeof rawData.archivedAt === 'number' ? { archivedAt: rawData.archivedAt } : {}),
  };
}

export function deserializeStudyModeDoc(docId: string, rawData: unknown): StudyMode {
  if (!isRecord(rawData)) {
    throw new Error(`Firestore study mode ${docId} is malformed.`);
  }
  if (!Array.isArray(rawData.steps)) {
    throw new Error(`Firestore study mode ${docId} is missing steps.`);
  }

  const modeId =
    typeof rawData.id === 'string' && rawData.id.trim().length > 0 ? rawData.id : docId;
  rawData.steps.forEach((step, index) => {
    assertStudyModeStep(step, modeId, index);
  });
  if (typeof rawData.isBuiltIn !== 'boolean') {
    throw new Error(`Firestore study mode ${modeId} has an invalid isBuiltIn flag.`);
  }
  if (rawData.isBuiltIn && (typeof rawData.builtInSourceId !== 'string' || rawData.builtInSourceId.trim().length === 0)) {
    throw new Error(`Firestore study mode ${modeId} has an invalid builtInSourceId.`);
  }
  if (!rawData.isBuiltIn && rawData.builtInSourceId !== undefined) {
    throw new Error(`Firestore study mode ${modeId} has an invalid builtInSourceId.`);
  }

  return {
    id: modeId,
    name: requireString(rawData.name, 'study mode name'),
    steps: rawData.steps as StudyMode['steps'],
    isBuiltIn: rawData.isBuiltIn,
    ...(typeof rawData.builtInSourceId === 'string'
      ? { builtInSourceId: rawData.builtInSourceId }
      : {}),
    updatedAt: requireFiniteNumber(rawData.updatedAt, `updatedAt for study mode ${modeId}`),
    ...(rawData.deletedAt != null && typeof rawData.deletedAt === 'number'
      ? { deletedAt: rawData.deletedAt }
      : {}),
  };
}

export function deserializeActivityDayCount(docId: string, rawData: unknown): number {
  if (!isRecord(rawData) || typeof rawData.count !== 'number' || !Number.isFinite(rawData.count)) {
    throw new Error(`Firestore activity day ${docId} is missing a valid count.`);
  }

  return rawData.count;
}
