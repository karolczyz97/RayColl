import { CARD_FILTERS, type CardFilter } from '../constants/cardFilters';
import { MIN_PAGE_COUNT, MAX_VISIBLE_PAGE_COUNT, MAX_STORED_PAGE_COUNT } from '../constants/pages';
import type { Flashcard, FlashcardGroup, StudyMode } from '../types/models';
import type { StoreData } from './persistence/localPersistence';
import { createSeedModes, isBuiltInModeSourceId } from './seed/seedModes';

export const CURRENT_SCHEMA_VERSION = 1;
export const DEFAULT_STUDY_FILTER: CardFilter = CARD_FILTERS.NEW_REVIEW;
const VALID_STUDY_FILTERS = new Set<CardFilter>(Object.values(CARD_FILTERS));

function coerceStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

export function normalizeStudyFilter(filter: unknown): CardFilter {
  return VALID_STUDY_FILTERS.has(filter as CardFilter)
    ? (filter as CardFilter)
    : DEFAULT_STUDY_FILTER;
}

function getStoredPageCount(group: FlashcardGroup, pageNames: string[], pageLanguages: string[]): number {
  const maxCardPages = Math.max(0, ...group.cards.map((c) => c.pages.length));
  const rawStored = Math.max(pageNames.length, pageLanguages.length, maxCardPages, MIN_PAGE_COUNT);
  return Math.min(rawStored, MAX_STORED_PAGE_COUNT);
}

function normalizeCard(card: Flashcard): Flashcard {
  return {
    ...card,
    contentUpdatedAt: card.contentUpdatedAt ?? 0,
    srsUpdatedAt: card.srsUpdatedAt ?? 0,
    deletedAt: card.deletedAt ?? undefined,
  };
}

export function normalizeGroup(group: FlashcardGroup): FlashcardGroup {
  const pageNames = coerceStringArray((group as { pageNames?: unknown }).pageNames);
  const pageLanguages = coerceStringArray((group as { pageLanguages?: unknown }).pageLanguages);
  const storedPageCount = getStoredPageCount(group, pageNames, pageLanguages);

  while (pageNames.length < storedPageCount) {
    pageNames.push(`Page ${pageNames.length + 1}`);
  }
  while (pageLanguages.length < storedPageCount) {
    pageLanguages.push('en-US');
  }

  const rawActivePageCount = (group as { activePageCount?: unknown }).activePageCount;
  const fallback = storedPageCount;
  const candidate =
    typeof rawActivePageCount === 'number' && Number.isFinite(rawActivePageCount)
      ? rawActivePageCount
      : fallback;
  const maxVisible = Math.min(MAX_VISIBLE_PAGE_COUNT, storedPageCount);
  const activePageCount = Math.max(
    MIN_PAGE_COUNT,
    Math.min(maxVisible, Math.floor(candidate)),
  );

  return {
    ...group,
    activePageCount,
    pageNames,
    pageLanguages,
    cards: group.cards.map(normalizeCard),
    studyFilter: normalizeStudyFilter(group.studyFilter),
    updatedAt: (group as { updatedAt?: number }).updatedAt ?? 0,
    deletedAt: (group as { deletedAt?: number | null }).deletedAt ?? undefined,
  };
}

export function normalizeStudyMode(mode: StudyMode): StudyMode {
  const rawMode = mode as StudyMode & {
    isBuiltIn?: unknown;
    builtInSourceId?: unknown;
  };
  const rawSourceId =
    typeof rawMode.builtInSourceId === 'string' && isBuiltInModeSourceId(rawMode.builtInSourceId)
      ? rawMode.builtInSourceId
      : undefined;
  const sourceId = rawSourceId ?? (isBuiltInModeSourceId(mode.id) ? mode.id : undefined);
  const isBuiltIn = sourceId ? true : rawMode.isBuiltIn === true;

  return {
    id: mode.id,
    name: mode.name,
    steps: mode.steps,
    isBuiltIn,
    ...(sourceId ? { builtInSourceId: sourceId } : {}),
    updatedAt: (mode as { updatedAt?: number }).updatedAt ?? 0,
    deletedAt: (mode as { deletedAt?: number | null }).deletedAt ?? undefined,
  };
}

export function normalizeStudyModes(modes: StudyMode[]): StudyMode[] {
  const normalizedModes = modes.map(normalizeStudyMode);
  const existingIds = new Set(normalizedModes.map((mode) => mode.id));
  const missingBuiltIns = createSeedModes().filter((mode) => !existingIds.has(mode.id));

  return [...normalizedModes, ...missingBuiltIns];
}

export function normalizeStoreData(data: StoreData): StoreData {
  return {
    groups: data.groups.map(normalizeGroup),
    studyModes: normalizeStudyModes(data.studyModes),
    activityHeatmap: data.activityHeatmap,
    schemaVersion: data.schemaVersion ?? CURRENT_SCHEMA_VERSION,
    lastSyncedAt: data.lastSyncedAt,
  };
}
