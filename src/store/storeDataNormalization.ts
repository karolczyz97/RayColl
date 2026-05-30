import { CARD_FILTERS, type CardFilter } from '../constants/cardFilters';
import type { FlashcardGroup, StudyMode } from '../types/models';
import type { StoreData } from './persistence/localPersistence';
import { createSeedModes, isBuiltInModeSourceId } from './seed/seedModes';

export const DEFAULT_STUDY_FILTER: CardFilter = CARD_FILTERS.NEW_REVIEW;
const MIN_ACTIVE_PAGE_COUNT = 2;
const MAX_ACTIVE_PAGE_COUNT = 5;

export function normalizeStudyFilter(filter: CardFilter | undefined): CardFilter {
  return filter ?? DEFAULT_STUDY_FILTER;
}

function getNormalizedActivePageCount(group: FlashcardGroup): number {
  const rawActivePageCount = (group as { activePageCount?: unknown }).activePageCount;
  const fallback = Math.max(group.pageNames.length, group.pageLanguages.length, MIN_ACTIVE_PAGE_COUNT);
  const candidate =
    typeof rawActivePageCount === 'number' && Number.isFinite(rawActivePageCount)
      ? rawActivePageCount
      : fallback;

  return Math.max(
    MIN_ACTIVE_PAGE_COUNT,
    Math.min(MAX_ACTIVE_PAGE_COUNT, Math.floor(candidate)),
  );
}

export function normalizeGroup(group: FlashcardGroup): FlashcardGroup {
  const pageNames = Array.isArray(group.pageNames) ? [...group.pageNames] : [];
  const pageLanguages = Array.isArray(group.pageLanguages) ? [...group.pageLanguages] : [];
  const activePageCount = getNormalizedActivePageCount({ ...group, pageNames, pageLanguages });

  while (pageNames.length < activePageCount) {
    pageNames.push(`Page ${pageNames.length + 1}`);
  }

  while (pageLanguages.length < activePageCount) {
    pageLanguages.push('en-US');
  }

  return {
    ...group,
    activePageCount,
    pageNames: pageNames.slice(0, activePageCount),
    pageLanguages: pageLanguages.slice(0, activePageCount),
    studyFilter: normalizeStudyFilter(group.studyFilter),
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
  };
}
