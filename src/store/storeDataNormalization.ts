import { CARD_FILTERS, type CardFilter } from '../constants/cardFilters';
import type { FlashcardGroup } from '../types/models';
import type { StoreData } from './persistence/localPersistence';

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
  const activePageCount = getNormalizedActivePageCount(group);
  const pageNames = [...group.pageNames];
  const pageLanguages = [...group.pageLanguages];

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

export function normalizeStoreData(data: StoreData): StoreData {
  return {
    groups: data.groups.map(normalizeGroup),
    studyModes: data.studyModes,
    activityHeatmap: data.activityHeatmap,
  };
}
