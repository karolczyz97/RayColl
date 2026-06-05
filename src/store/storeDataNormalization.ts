import { CARD_FILTERS, DEFAULT_STUDY_FILTER, type CardFilter } from '@/constants/cardFilters';
import { MIN_PAGE_COUNT, MAX_VISIBLE_PAGE_COUNT, MAX_STORED_PAGE_COUNT, clampActivePageCount } from '@/constants/pages';
import type { Flashcard, FlashcardGroup, SrsState, StudyMode, StoreData } from '@/types/models';
import { createSeedModes, isBuiltInModeSourceId } from './seed/seedModes';
import { coerceStringArray } from '@/utils/array';
import { uid } from '@/utils/id';
import { isRecord } from '@/utils/types';

const VALID_SRS_STATES: ReadonlySet<SrsState['state']> = new Set([0, 1, 2, 3]);

// Canonical definition lives in constants/cardFilters; re-exported here to keep
// existing import sites stable.
export { DEFAULT_STUDY_FILTER };
const VALID_STUDY_FILTERS = new Set<CardFilter>(Object.values(CARD_FILTERS));

export function normalizeStudyFilter(filter: unknown): CardFilter {
  return VALID_STUDY_FILTERS.has(filter as CardFilter)
    ? (filter as CardFilter)
    : DEFAULT_STUDY_FILTER;
}

/**
 * Pads page names and languages up to `count`, filling gaps with the canonical
 * defaults ("Page N" / "en-US"). Single source of truth for page metadata
 * padding shared by normalization and the setVisiblePageCount action.
 */
export function padPageMetadata(
  pageNames: string[],
  pageLanguages: string[],
  count: number,
): { pageNames: string[]; pageLanguages: string[] } {
  const names = [...pageNames];
  const langs = [...pageLanguages];
  while (names.length < count) {
    names.push(`Page ${names.length + 1}`);
  }
  while (langs.length < count) {
    langs.push('en-US');
  }
  return { pageNames: names, pageLanguages: langs };
}

export function getStoredPageCount(group: FlashcardGroup, pageNames: string[], pageLanguages: string[]): number {
  const maxCardPages = Math.max(0, ...group.cards.map((c) => c.pages.length));
  const rawStored = Math.max(pageNames.length, pageLanguages.length, maxCardPages, MIN_PAGE_COUNT);
  return Math.min(rawStored, MAX_STORED_PAGE_COUNT);
}

function normalizeCard(card: Flashcard): Flashcard {
  return {
    ...card,
    srsState: VALID_SRS_STATES.has(card.srsState.state)
      ? card.srsState
      : { ...card.srsState, state: 0 as SrsState['state'] },
    contentUpdatedAt: card.contentUpdatedAt ?? 0,
    srsUpdatedAt: card.srsUpdatedAt ?? 0,
    deletedAt: card.deletedAt ?? undefined,
  };
}

export function normalizeGroup(group: FlashcardGroup): FlashcardGroup {
  const rawNames = coerceStringArray((group as { pageNames?: unknown }).pageNames);
  const rawLanguages = coerceStringArray((group as { pageLanguages?: unknown }).pageLanguages);
  const storedPageCount = getStoredPageCount(group, rawNames, rawLanguages);
  const { pageNames, pageLanguages } = padPageMetadata(rawNames, rawLanguages, storedPageCount);

  const rawActivePageCount = (group as { activePageCount?: unknown }).activePageCount;
  const fallback = storedPageCount;
  const candidate =
    typeof rawActivePageCount === 'number' && Number.isFinite(rawActivePageCount)
      ? rawActivePageCount
      : fallback;
  const maxVisible = Math.min(MAX_VISIBLE_PAGE_COUNT, storedPageCount);
  const activePageCount = clampActivePageCount(candidate, maxVisible);

  return {
    ...group,
    activePageCount,
    pageNames,
    pageLanguages,
    cards: group.cards.map(normalizeCard),
    studyFilter: normalizeStudyFilter(group.studyFilter),
    updatedAt: (group as { updatedAt?: number }).updatedAt ?? 0,
    deletedAt: (group as { deletedAt?: number | null }).deletedAt ?? undefined,
    archivedAt: (group as { archivedAt?: number | null }).archivedAt ?? undefined,
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
    steps: mode.steps.map((step) => (step.id ? step : { ...step, id: uid() })),
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

export function normalizeActivityHeatmap(value: unknown): Record<string, number> {
  if (!isRecord(value)) {
    return {};
  }

  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, number] => {
      const [, count] = entry;
      return typeof count === 'number' && Number.isFinite(count);
    }),
  );
}

export function normalizeStoreData(data: StoreData): StoreData {
  return {
    groups: data.groups.map(normalizeGroup),
    studyModes: normalizeStudyModes(data.studyModes),
    activityHeatmap: normalizeActivityHeatmap(data.activityHeatmap),
    lastSyncedAt: data.lastSyncedAt,
  };
}
