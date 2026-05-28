import { CARD_FILTERS, type CardFilter } from '../constants/cardFilters';
import type { FlashcardGroup } from '../types/models';
import type { StoreData } from './persistence/localPersistence';

export const DEFAULT_STUDY_FILTER: CardFilter = CARD_FILTERS.NEW_REVIEW;

export function normalizeStudyFilter(filter: CardFilter | undefined): CardFilter {
  return filter ?? DEFAULT_STUDY_FILTER;
}

function getNormalizedActivePageCount(group: FlashcardGroup): number {
  return group.activePageCount ?? Math.max(group.pageNames.length, group.pageLanguages.length);
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
    pageNames,
    pageLanguages,
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
